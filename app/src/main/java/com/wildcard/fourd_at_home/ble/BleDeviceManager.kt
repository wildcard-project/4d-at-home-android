package com.wildcard.fourd_at_home.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * BLEデバイスマネージャー
 * 複数デバイスへの接続を管理する
 */
@Singleton
class BleDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleDeviceManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 接続中のGATT
    private val gattConnections = mutableMapOf<String, BluetoothGatt>()
    
    // Command Characteristic
    private val commandCharacteristics = mutableMapOf<String, BluetoothGattCharacteristic>()
    
    // Status Characteristic
    private val statusCharacteristics = mutableMapOf<String, BluetoothGattCharacteristic>()

    // 接続状態
    private val _connections = MutableStateFlow<Map<String, BleConnection>>(emptyMap())
    val connections: StateFlow<Map<String, BleConnection>> = _connections.asStateFlow()

    // ステータス通知
    private val _statusNotifications = MutableSharedFlow<StatusNotification>()
    val statusNotifications: SharedFlow<StatusNotification> = _statusNotifications.asSharedFlow()

    // コマンドログ
    private val _commandLog = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    val commandLog: StateFlow<List<CommandLogEntry>> = _commandLog.asStateFlow()

    // 再接続ジョブ
    private val reconnectJobs = mutableMapOf<String, Job>()
    
    // デバイスごとの書き込みMutex（同一デバイスへの書き込みを直列化）
    private val writeMutexMap = ConcurrentHashMap<String, Mutex>()
    
    // 書き込み完了待ち用のコールバック
    private val writeCallbacks = ConcurrentHashMap<String, kotlin.coroutines.Continuation<Result<Unit>>>()
    
    private fun getWriteMutex(address: String): Mutex {
        return writeMutexMap.getOrPut(address) { Mutex() }
    }

    /**
     * デバイスに接続
     */
    suspend fun connect(device: ScannedDevice): Result<Unit> {
        val address = device.address
        Log.d(TAG, "接続開始: ${device.name} ($address)")

        // 既存の接続をチェック
        if (gattConnections.containsKey(address)) {
            val currentState = _connections.value[address]?.state
            if (currentState == ConnectionState.READY) {
                Log.d(TAG, "既に接続済み: $address")
                return Result.success(Unit)
            }
        }

        // 接続状態を更新
        updateConnectionState(device, ConnectionState.CONNECTING)

        return try {
            withTimeout(BleConstants.CONNECTION_TIMEOUT_MS) {
                connectInternal(device)
            }
        } catch (e: Exception) {
            Log.e(TAG, "接続失敗: ${e.message}", e)
            updateConnectionState(
                device,
                ConnectionState.ERROR,
                errorMessage = e.message ?: "接続に失敗しました"
            )
            Result.failure(e)
        }
    }

    private suspend fun connectInternal(device: ScannedDevice): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val address = device.address

            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    Log.d(TAG, "接続状態変更: status=$status, newState=$newState")

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "接続成功: $address")
                            updateConnectionState(device, ConnectionState.CONNECTED)

                            // サービス検出
                            updateConnectionState(device, ConnectionState.DISCOVERING_SERVICES)
                            try {
                                gatt.discoverServices()
                            } catch (e: SecurityException) {
                                Log.e(TAG, "サービス検出開始失敗", e)
                                updateConnectionState(device, ConnectionState.ERROR, "権限エラー")
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(e))
                                }
                            }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d(TAG, "切断: $address")
                            handleDisconnection(address, device)
                            if (continuation.isActive) {
                                if (status != BluetoothGatt.GATT_SUCCESS) {
                                    continuation.resume(
                                        Result.failure(
                                            Exception("接続失敗 (status: $status)")
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "サービス検出成功: $address")

                        // サービスとCharacteristicを取得
                        val service = gatt.getService(BleConstants.SERVICE_UUID)
                        if (service == null) {
                            Log.e(TAG, "サービスが見つかりません")
                            updateConnectionState(device, ConnectionState.ERROR, "サービスが見つかりません")
                            if (continuation.isActive) {
                                continuation.resume(
                                    Result.failure(Exception("サービスが見つかりません"))
                                )
                            }
                            return
                        }

                        val commandChar = service.getCharacteristic(BleConstants.COMMAND_CHAR_UUID)
                        val statusChar = service.getCharacteristic(BleConstants.STATUS_CHAR_UUID)

                        if (commandChar == null) {
                            Log.e(TAG, "Command Characteristicが見つかりません")
                            updateConnectionState(device, ConnectionState.ERROR, "Characteristicが見つかりません")
                            if (continuation.isActive) {
                                continuation.resume(
                                    Result.failure(Exception("Command Characteristicが見つかりません"))
                                )
                            }
                            return
                        }

                        commandCharacteristics[address] = commandChar
                        statusChar?.let { statusCharacteristics[address] = it }

                        // Status通知を有効化
                        statusChar?.let {
                            scope.launch {
                                enableNotifications(gatt, it)
                            }
                        }

                        updateConnectionState(device, ConnectionState.READY)
                        Log.d(TAG, "接続準備完了: $address")

                        if (continuation.isActive) {
                            continuation.resume(Result.success(Unit))
                        }
                    } else {
                        Log.e(TAG, "サービス検出失敗: status=$status")
                        updateConnectionState(device, ConnectionState.ERROR, "サービス検出失敗")
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("サービス検出失敗 (status: $status)"))
                            )
                        }
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    if (characteristic.uuid == BleConstants.STATUS_CHAR_UUID) {
                        handleStatusNotification(address, device.deviceType, value)
                    }
                }

                @Deprecated("Deprecated in API 33")
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    if (characteristic.uuid == BleConstants.STATUS_CHAR_UUID) {
                        handleStatusNotification(address, device.deviceType, characteristic.value)
                    }
                }
                
                // 書き込み完了コールバック
                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    Log.d(TAG, "onCharacteristicWrite: address=$address, status=$status")
                    val callback = writeCallbacks.remove(address)
                    if (callback != null) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "コマンド送信完了: address=$address")
                            addCommandLog(
                                address = address,
                                deviceType = device.deviceType,
                                command = "WRITE_SUCCESS",
                                direction = CommandDirection.TX,
                                status = CommandStatus.SUCCESS
                            )
                            callback.resume(Result.success(Unit))
                        } else {
                            Log.e(TAG, "コマンド送信完了（エラー）: address=$address, status=$status")
                            addCommandLog(
                                address = address,
                                deviceType = device.deviceType,
                                command = "WRITE_FAILED",
                                direction = CommandDirection.TX,
                                status = CommandStatus.FAILED,
                                errorMessage = "GATT status: $status"
                            )
                            callback.resume(Result.failure(
                                Exception("書き込み失敗 (GATT status: $status)")
                            ))
                        }
                    }
                }
            }

            try {
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.device.connectGatt(
                        context,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    device.device.connectGatt(context, false, gattCallback)
                }

                gattConnections[address] = gatt

                continuation.invokeOnCancellation {
                    Log.d(TAG, "接続キャンセル: $address")
                    try {
                        gatt.close()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "GATT close失敗", e)
                    }
                    gattConnections.remove(address)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "GATT接続失敗 (権限エラー)", e)
                updateConnectionState(device, ConnectionState.ERROR, "権限エラー")
                continuation.resume(Result.failure(e))
            }
        }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                Log.d(TAG, "通知有効化成功")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "通知有効化失敗 (権限エラー)", e)
        }
    }

    /**
     * デバイスを切断
     */
    fun disconnect(address: String) {
        Log.d(TAG, "切断開始: $address")

        // 再接続ジョブをキャンセル
        reconnectJobs[address]?.cancel()
        reconnectJobs.remove(address)

        val gatt = gattConnections[address]
        if (gatt != null) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "切断失敗 (権限エラー)", e)
            }
        }

        gattConnections.remove(address)
        commandCharacteristics.remove(address)
        statusCharacteristics.remove(address)
        writeMutexMap.remove(address)
        writeCallbacks.remove(address)
        
        _connections.value = _connections.value.toMutableMap().apply {
            remove(address)
        }

        Log.d(TAG, "切断完了: $address")
    }

    /**
     * 全デバイスを切断
     */
    fun disconnectAll() {
        Log.d(TAG, "全デバイス切断")
        gattConnections.keys.toList().forEach { address ->
            disconnect(address)
        }
    }

    /**
     * コマンドを送信
     * 同一デバイスへの書き込みはMutexで直列化され、書き込み完了を待つ
     */
    suspend fun sendCommand(address: String, command: ByteArray): Result<Unit> {
        val gatt = gattConnections[address]
        val characteristic = commandCharacteristics[address]

        if (gatt == null || characteristic == null) {
            return Result.failure(Exception("デバイスが接続されていません"))
        }

        val connection = _connections.value[address]
        if (connection?.state != ConnectionState.READY) {
            return Result.failure(Exception("デバイスが準備できていません"))
        }

        // 同一デバイスへの書き込みを直列化
        val mutex = getWriteMutex(address)
        
        return mutex.withLock {
            try {
                withTimeout(BleConstants.WRITE_TIMEOUT_MS) {
                    writeCharacteristicAndWait(gatt, characteristic, command, address, connection.deviceType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "コマンド送信失敗: ${e.message}", e)
                addCommandLog(
                    address = address,
                    deviceType = connection.deviceType,
                    command = command.toHexString(),
                    direction = CommandDirection.TX,
                    status = CommandStatus.FAILED,
                    errorMessage = e.message
                )
                Result.failure(e)
            }
        }
    }

    /**
     * 書き込みを行い、onCharacteristicWriteコールバックを待つ
     */
    private suspend fun writeCharacteristicAndWait(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        address: String,
        deviceType: DeviceType
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            // コールバック登録
            writeCallbacks[address] = continuation
            
            val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

            if (!writeResult) {
                // 書き込みリクエストが拒否された場合
                writeCallbacks.remove(address)
                Log.e(TAG, "コマンド送信失敗（BLEスタックビジー）: ${value.toHexString()}")
                addCommandLog(
                    address = address,
                    deviceType = deviceType,
                    command = value.toHexString(),
                    direction = CommandDirection.TX,
                    status = CommandStatus.FAILED,
                    errorMessage = "BLEスタックビジー"
                )
                continuation.resume(Result.failure(Exception("コマンド送信に失敗しました（BLEスタックビジー）")))
            } else {
                Log.d(TAG, "コマンド送信リクエスト成功: ${value.toHexString()}")
                // onCharacteristicWriteコールバックで continuation.resume() される
            }
            
            continuation.invokeOnCancellation {
                writeCallbacks.remove(address)
            }
        } catch (e: SecurityException) {
            writeCallbacks.remove(address)
            Log.e(TAG, "コマンド送信失敗 (権限エラー)", e)
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * 接続済みで準備完了のデバイスを取得
     */
    fun getReadyDevices(): List<BleConnection> {
        return _connections.value.values.filter { it.state == ConnectionState.READY }
    }

    /**
     * 特定のデバイスタイプの接続を取得
     */
    fun getDeviceByType(type: DeviceType): BleConnection? {
        return _connections.value.values.find { 
            it.deviceType == type && it.state == ConnectionState.READY 
        }
    }

    private fun updateConnectionState(
        device: ScannedDevice,
        state: ConnectionState,
        errorMessage: String? = null
    ) {
        val connection = BleConnection(
            device = device.device,
            name = device.name,
            address = device.address,
            deviceType = device.deviceType,
            state = state,
            rssi = device.rssi,
            errorMessage = errorMessage
        )
        _connections.value = _connections.value.toMutableMap().apply {
            put(device.address, connection)
        }
    }

    private fun handleDisconnection(address: String, device: ScannedDevice) {
        gattConnections.remove(address)
        commandCharacteristics.remove(address)
        statusCharacteristics.remove(address)

        updateConnectionState(device, ConnectionState.DISCONNECTED)

        // 自動再接続
        startReconnection(device)
    }

    private fun startReconnection(device: ScannedDevice) {
        val address = device.address
        
        // 既存のジョブをキャンセル
        reconnectJobs[address]?.cancel()
        
        reconnectJobs[address] = scope.launch {
            repeat(BleConstants.MAX_RECONNECT_ATTEMPTS) { attempt ->
                Log.d(TAG, "再接続試行 ${attempt + 1}/${BleConstants.MAX_RECONNECT_ATTEMPTS}: $address")
                delay(BleConstants.RECONNECT_DELAY_MS)

                val result = connect(device)
                if (result.isSuccess) {
                    Log.d(TAG, "再接続成功: $address")
                    return@launch
                }
            }
            Log.w(TAG, "再接続失敗 (最大試行回数に達しました): $address")
        }
    }

    private fun handleStatusNotification(address: String, deviceType: DeviceType, value: ByteArray) {
        Log.d(TAG, "ステータス通知受信: ${value.toHexString()}")

        scope.launch {
            _statusNotifications.emit(
                StatusNotification(
                    address = address,
                    deviceType = deviceType,
                    data = value
                )
            )
        }

        addCommandLog(
            address = address,
            deviceType = deviceType,
            command = value.toHexString(),
            direction = CommandDirection.RX,
            status = CommandStatus.SUCCESS
        )
    }

    private fun addCommandLog(
        address: String,
        deviceType: DeviceType,
        command: String,
        direction: CommandDirection,
        status: CommandStatus,
        errorMessage: String? = null
    ) {
        val entry = CommandLogEntry(
            timestamp = System.currentTimeMillis(),
            direction = direction,
            deviceAddress = address,
            deviceType = deviceType,
            command = command,
            status = status,
            errorMessage = errorMessage
        )
        _commandLog.value = (_commandLog.value + entry).takeLast(100)
    }

    /**
     * コマンドログをクリア
     */
    fun clearCommandLog() {
        _commandLog.value = emptyList()
    }
}

/**
 * ステータス通知
 */
data class StatusNotification(
    val address: String,
    val deviceType: DeviceType,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StatusNotification
        if (address != other.address) return false
        if (deviceType != other.deviceType) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + deviceType.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * ByteArrayをHex文字列に変換
 */
fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
