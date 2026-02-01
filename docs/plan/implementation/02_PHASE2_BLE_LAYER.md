# Phase 2: BLE通信レイヤー

**期間目安**: 2-3日  
**前提条件**: Phase 1 完了

---

## 📋 タスク一覧

| # | タスク | 優先度 | 完了条件 |
|:-:|:-------|:------:|:---------|
| 2.1 | BLE定数・モデル定義 | 必須 | UUID、状態定義完了 |
| 2.2 | BleScanner実装 | 必須 | 4D_デバイス検出 |
| 2.3 | BleDeviceManager実装 | 必須 | 接続・切断動作 |
| 2.4 | CommandSender実装 | 必須 | コマンド送信動作 |
| 2.5 | Hilt DIモジュール | 必須 | 依存注入設定 |
| 2.6 | 設定画面UI | 必須 | スキャン・接続UI |
| 2.7 | 権限ハンドリング | 必須 | BLE権限リクエスト |

---

## 2.1 BLE定数・モデル定義

### 2.1.1 ble/BleConstants.kt

```kotlin
package com.wildcard.fourd_at_home.ble

import java.util.UUID

object BleConstants {
    // === Service/Characteristic UUIDs ===
    val SERVICE_UUID: UUID = UUID.fromString("4D580001-0000-1000-8000-00805F9B34FB")
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("4D580002-0000-1000-8000-00805F9B34FB")
    val STATUS_CHAR_UUID: UUID = UUID.fromString("4D580003-0000-1000-8000-00805F9B34FB")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // === デバイス名パターン ===
    // 4D_ES_XXXX  : EffectStation
    // 4D_AD1_XXXX : ActionDrive Motor1
    // 4D_AD2_XXXX : ActionDrive Motor2
    val DEVICE_NAME_PATTERN = Regex("4D_(ES|AD1|AD2)_[0-9A-Fa-f]{4}")
    
    // デバイスタイプ判定用プレフィックス
    const val PREFIX_EFFECT_STATION = "4D_ES_"
    const val PREFIX_ACTION_DRIVE_1 = "4D_AD1_"
    const val PREFIX_ACTION_DRIVE_2 = "4D_AD2_"

    // === タイムアウト設定 (ms) ===
    const val SCAN_TIMEOUT_MS = 15_000L
    const val CONNECTION_TIMEOUT_MS = 10_000L
    const val WRITE_TIMEOUT_MS = 5_000L
    const val GATT_OPERATION_DELAY_MS = 100L

    // === 再接続設定 ===
    const val MAX_RECONNECT_ATTEMPTS = 3
    const val RECONNECT_DELAY_MS = 2_000L
}
```

### 2.1.2 ble/BleModels.kt

```kotlin
package com.wildcard.fourd_at_home.ble

import android.bluetooth.BluetoothDevice

/**
 * デバイスタイプ
 */
enum class DeviceType(val prefix: String, val displayName: String) {
    EFFECT_STATION(BleConstants.PREFIX_EFFECT_STATION, "EffectStation"),
    ACTION_DRIVE_1(BleConstants.PREFIX_ACTION_DRIVE_1, "ActionDrive Motor1"),
    ACTION_DRIVE_2(BleConstants.PREFIX_ACTION_DRIVE_2, "ActionDrive Motor2"),
    UNKNOWN("", "不明");

    companion object {
        fun fromDeviceName(name: String?): DeviceType {
            if (name == null) return UNKNOWN
            return entries.find { name.startsWith(it.prefix) } ?: UNKNOWN
        }
    }
}

/**
 * 接続状態
 */
enum class ConnectionState {
    DISCONNECTED,          // 未接続
    CONNECTING,            // 接続中
    CONNECTED,             // 接続済み（サービス検出前）
    DISCOVERING_SERVICES,  // サービス検出中
    READY,                 // 準備完了（コマンド送信可能）
    DISCONNECTING,         // 切断中
    ERROR                  // エラー
}

/**
 * スキャンされたデバイス
 */
data class ScannedDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: DeviceType,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val signalStrength: SignalStrength
        get() = when {
            rssi >= -50 -> SignalStrength.EXCELLENT
            rssi >= -60 -> SignalStrength.GOOD
            rssi >= -70 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }
}

/**
 * 信号強度
 */
enum class SignalStrength(val displayName: String, val emoji: String) {
    EXCELLENT("非常に強い", "📶"),
    GOOD("強い", "📶"),
    FAIR("普通", "📶"),
    WEAK("弱い", "📶")
}

/**
 * BLE接続情報
 */
data class BleConnection(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val deviceType: DeviceType,
    val state: ConnectionState,
    val rssi: Int = 0,
    val errorMessage: String? = null
)

/**
 * コマンドログエントリ
 */
data class CommandLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: CommandDirection,
    val deviceAddress: String,
    val deviceType: DeviceType,
    val command: String,
    val status: CommandStatus,
    val errorMessage: String? = null
)

enum class CommandDirection { TX, RX }

enum class CommandStatus { 
    SUCCESS, 
    PENDING, 
    FAILED,
    TIMEOUT 
}

/**
 * BLEエラー
 */
sealed class BleError(open val message: String) {
    data class BluetoothDisabled(
        override val message: String = "Bluetoothが無効です"
    ) : BleError(message)

    data class PermissionDenied(
        override val message: String = "Bluetooth権限がありません"
    ) : BleError(message)

    data class ScanFailed(
        override val message: String,
        val errorCode: Int
    ) : BleError(message)

    data class ConnectionFailed(
        override val message: String,
        val statusCode: Int? = null
    ) : BleError(message)

    data class ServiceDiscoveryFailed(
        override val message: String = "サービスが見つかりません"
    ) : BleError(message)

    data class CharacteristicNotFound(
        override val message: String = "Characteristicが見つかりません"
    ) : BleError(message)

    data class WriteFailed(
        override val message: String,
        val statusCode: Int? = null
    ) : BleError(message)

    data class Timeout(
        override val message: String = "タイムアウトしました"
    ) : BleError(message)

    data class DeviceNotFound(
        override val message: String = "デバイスが見つかりません"
    ) : BleError(message)
}
```

---

## 2.2 BleScanner実装

### 2.2.1 ble/BleScanner.kt

```kotlin
package com.wildcard.fourd_at_home.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleScanner"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    // === StateFlows ===
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    val scannedDevices: StateFlow<Map<String, ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _error = MutableStateFlow<BleError?>(null)
    val error: StateFlow<BleError?> = _error.asStateFlow()

    // === スキャン管理 ===
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // === ScanCallback ===
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return
            
            // 4D_で始まるデバイスのみ処理
            if (!BleConstants.DEVICE_NAME_PATTERN.matches(deviceName)) {
                return
            }

            val scannedDevice = ScannedDevice(
                device = device,
                name = deviceName,
                address = device.address,
                rssi = result.rssi,
                deviceType = DeviceType.fromDeviceName(deviceName)
            )

            _scannedDevices.value = _scannedDevices.value + (device.address to scannedDevice)
            Log.d(TAG, "Found device: $deviceName (${device.address})")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
            _error.value = BleError.ScanFailed(
                message = "スキャンに失敗しました (code: $errorCode)",
                errorCode = errorCode
            )
        }
    }

    /**
     * BLEスキャンを開始
     */
    @SuppressLint("MissingPermission")
    fun startScan(
        timeoutMs: Long = BleConstants.SCAN_TIMEOUT_MS,
        filterByServiceUuid: Boolean = false
    ) {
        // 前提チェック
        if (!isBluetoothEnabled()) {
            _error.value = BleError.BluetoothDisabled()
            return
        }
        if (!hasRequiredPermissions()) {
            _error.value = BleError.PermissionDenied()
            return
        }
        if (_isScanning.value) {
            Log.w(TAG, "Already scanning")
            return
        }

        val scanner = bleScanner
        if (scanner == null) {
            _error.value = BleError.BluetoothDisabled("BLEスキャナーを取得できません")
            return
        }

        // 前回の結果をクリア
        _scannedDevices.value = emptyMap()
        _error.value = null
        _isScanning.value = true

        // スキャン設定
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // フィルター（オプション）
        val filters = if (filterByServiceUuid) {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                    .build()
            )
        } else {
            null // フィルターなし（デバイス名でフィルタリング）
        }

        Log.d(TAG, "Starting BLE scan...")
        scanner.startScan(filters, settings, scanCallback)

        // タイムアウト処理
        scanJob = scope.launch {
            delay(timeoutMs)
            stopScan()
        }
    }

    /**
     * BLEスキャンを停止
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return

        scanJob?.cancel()
        scanJob = null

        if (hasRequiredPermissions()) {
            bleScanner?.stopScan(scanCallback)
        }
        _isScanning.value = false
        Log.d(TAG, "Scan stopped. Found ${_scannedDevices.value.size} devices")
    }

    /**
     * 特定デバイスを取得
     */
    fun getDevice(address: String): ScannedDevice? {
        return _scannedDevices.value[address]
    }

    /**
     * Bluetoothが有効か確認
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * 必要な権限があるか確認
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 必要な権限のリストを取得
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * エラーをクリア
     */
    fun clearError() {
        _error.value = null
    }
}
```

---

## 2.3 BleDeviceManager実装

### 2.3.1 ble/BleDeviceManager.kt

```kotlin
package com.wildcard.fourd_at_home.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BleDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleDeviceManager"
    }

    // === 接続管理 ===
    private val gattMap = ConcurrentHashMap<String, BluetoothGatt>()
    private val commandCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()
    private val statusCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    // === StateFlows ===
    private val _connections = MutableStateFlow<Map<String, BleConnection>>(emptyMap())
    val connections: StateFlow<Map<String, BleConnection>> = _connections.asStateFlow()

    private val _commandLog = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    val commandLog: StateFlow<List<CommandLogEntry>> = _commandLog.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val connectionJobs = ConcurrentHashMap<String, Job>()

    /**
     * デバイスに接続
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: ScannedDevice): Result<BleConnection> {
        val address = device.address

        // 既に接続済みの場合
        val existingConnection = _connections.value[address]
        if (existingConnection?.state == ConnectionState.READY) {
            return Result.success(existingConnection)
        }

        // 接続状態を更新
        updateConnectionState(device, ConnectionState.CONNECTING)

        return try {
            withTimeout(BleConstants.CONNECTION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val callback = createGattCallback(device, continuation)
                    
                    val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.device.connectGatt(
                            context,
                            false,
                            callback,
                            BluetoothDevice.TRANSPORT_LE
                        )
                    } else {
                        device.device.connectGatt(context, false, callback)
                    }

                    if (gatt == null) {
                        continuation.resume(
                            Result.failure(Exception("GATT接続に失敗しました"))
                        )
                    } else {
                        gattMap[address] = gatt
                    }

                    continuation.invokeOnCancellation {
                        gatt?.close()
                        gattMap.remove(address)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            updateConnectionState(device, ConnectionState.ERROR, e.message)
            Result.failure(e)
        }
    }

    /**
     * デバイスを切断
     */
    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        val gatt = gattMap[address] ?: return
        
        val connection = _connections.value[address]
        if (connection != null) {
            updateConnectionState(
                address = address,
                deviceType = connection.deviceType,
                name = connection.name,
                state = ConnectionState.DISCONNECTING
            )
        }

        gatt.disconnect()
    }

    /**
     * 全デバイスを切断
     */
    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        gattMap.keys.toList().forEach { address ->
            disconnect(address)
        }
    }

    /**
     * コマンドを送信
     */
    @SuppressLint("MissingPermission")
    suspend fun sendCommand(address: String, command: String): Result<Unit> {
        val gatt = gattMap[address]
            ?: return Result.failure(Exception("デバイスが接続されていません"))
        
        val characteristic = commandCharacteristics[address]
            ?: return Result.failure(Exception("Characteristicが見つかりません"))

        val connection = _connections.value[address]
        val deviceType = connection?.deviceType ?: DeviceType.UNKNOWN

        // ログに追加（送信中）
        addCommandLog(
            direction = CommandDirection.TX,
            deviceAddress = address,
            deviceType = deviceType,
            command = command,
            status = CommandStatus.PENDING
        )

        return try {
            val data = command.toByteArray(Charsets.UTF_8)
            
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(characteristic)
            }

            // 操作間隔を確保
            delay(BleConstants.GATT_OPERATION_DELAY_MS)

            if (success) {
                updateCommandLogStatus(address, command, CommandStatus.SUCCESS)
                Log.d(TAG, "Command sent: $command to $address")
                Result.success(Unit)
            } else {
                updateCommandLogStatus(address, command, CommandStatus.FAILED, "書き込み失敗")
                Result.failure(Exception("コマンド送信に失敗しました"))
            }
        } catch (e: Exception) {
            updateCommandLogStatus(address, command, CommandStatus.FAILED, e.message)
            Result.failure(e)
        }
    }

    /**
     * 特定デバイスタイプの接続を取得
     */
    fun getConnectionByType(deviceType: DeviceType): BleConnection? {
        return _connections.value.values.find { 
            it.deviceType == deviceType && it.state == ConnectionState.READY 
        }
    }

    /**
     * READY状態の接続を取得
     */
    fun getReadyConnections(): List<BleConnection> {
        return _connections.value.values.filter { it.state == ConnectionState.READY }
    }

    /**
     * ログをクリア
     */
    fun clearLog() {
        _commandLog.value = emptyList()
    }

    // === Private Methods ===

    @SuppressLint("MissingPermission")
    private fun createGattCallback(
        device: ScannedDevice,
        continuation: kotlinx.coroutines.CancellableContinuation<Result<BleConnection>>
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val address = gatt.device.address
                Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to $address")
                        updateConnectionState(device, ConnectionState.DISCOVERING_SERVICES)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from $address")
                        cleanup(address)
                        updateConnectionState(device, ConnectionState.DISCONNECTED)
                        
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("接続が切断されました"))
                            )
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val address = gatt.device.address

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered for $address")

                    // サービスを検索
                    val service = gatt.getService(BleConstants.SERVICE_UUID)
                    if (service == null) {
                        Log.e(TAG, "4DX Service not found")
                        updateConnectionState(device, ConnectionState.ERROR, "サービスが見つかりません")
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("4DX Serviceが見つかりません"))
                            )
                        }
                        return
                    }

                    // Command Characteristicを取得
                    val commandChar = service.getCharacteristic(BleConstants.COMMAND_CHAR_UUID)
                    if (commandChar != null) {
                        commandCharacteristics[address] = commandChar
                    } else {
                        Log.w(TAG, "Command Characteristic not found")
                    }

                    // Status Characteristicを取得
                    val statusChar = service.getCharacteristic(BleConstants.STATUS_CHAR_UUID)
                    if (statusChar != null) {
                        statusCharacteristics[address] = statusChar
                        // Notifyを有効化
                        enableNotifications(gatt, statusChar)
                    }

                    // 接続完了
                    val connection = BleConnection(
                        device = device.device,
                        name = device.name,
                        address = address,
                        deviceType = device.deviceType,
                        state = ConnectionState.READY,
                        rssi = device.rssi
                    )
                    _connections.value = _connections.value + (address to connection)
                    
                    if (continuation.isActive) {
                        continuation.resume(Result.success(connection))
                    }
                } else {
                    Log.e(TAG, "Service discovery failed: $status")
                    updateConnectionState(device, ConnectionState.ERROR, "サービス検出失敗")
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(Exception("サービス検出に失敗しました"))
                        )
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == BleConstants.STATUS_CHAR_UUID) {
                    @Suppress("DEPRECATION")
                    val status = characteristic.getStringValue(0)
                    Log.d(TAG, "Status received: $status from ${gatt.device.address}")
                    
                    addCommandLog(
                        direction = CommandDirection.RX,
                        deviceAddress = gatt.device.address,
                        deviceType = device.deviceType,
                        command = status,
                        status = CommandStatus.SUCCESS
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun updateConnectionState(
        device: ScannedDevice,
        state: ConnectionState,
        errorMessage: String? = null
    ) {
        updateConnectionState(
            address = device.address,
            deviceType = device.deviceType,
            name = device.name,
            state = state,
            errorMessage = errorMessage
        )
    }

    private fun updateConnectionState(
        address: String,
        deviceType: DeviceType,
        name: String,
        state: ConnectionState,
        errorMessage: String? = null
    ) {
        val existing = _connections.value[address]
        val connection = BleConnection(
            device = existing?.device ?: return,
            name = name,
            address = address,
            deviceType = deviceType,
            state = state,
            rssi = existing.rssi,
            errorMessage = errorMessage
        )
        _connections.value = _connections.value + (address to connection)
    }

    @SuppressLint("MissingPermission")
    private fun cleanup(address: String) {
        gattMap[address]?.close()
        gattMap.remove(address)
        commandCharacteristics.remove(address)
        statusCharacteristics.remove(address)
    }

    private fun addCommandLog(
        direction: CommandDirection,
        deviceAddress: String,
        deviceType: DeviceType,
        command: String,
        status: CommandStatus,
        errorMessage: String? = null
    ) {
        val entry = CommandLogEntry(
            direction = direction,
            deviceAddress = deviceAddress,
            deviceType = deviceType,
            command = command,
            status = status,
            errorMessage = errorMessage
        )
        _commandLog.value = (_commandLog.value + entry).takeLast(100)
    }

    private fun updateCommandLogStatus(
        deviceAddress: String,
        command: String,
        status: CommandStatus,
        errorMessage: String? = null
    ) {
        val logs = _commandLog.value.toMutableList()
        val index = logs.indexOfLast { 
            it.deviceAddress == deviceAddress && 
            it.command == command && 
            it.status == CommandStatus.PENDING 
        }
        if (index >= 0) {
            logs[index] = logs[index].copy(status = status, errorMessage = errorMessage)
            _commandLog.value = logs
        }
    }
}
```

---

## 2.4 CommandSender実装

### 2.4.1 ble/CommandSender.kt

```kotlin
package com.wildcard.fourd_at_home.ble

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * コマンド送信を管理するクラス
 * デバイスごとのコマンド送信を順序付きで実行
 */
@Singleton
class CommandSender @Inject constructor(
    private val deviceManager: BleDeviceManager
) {
    private val mutex = Mutex()

    /**
     * EffectStationにコマンドを送信
     */
    suspend fun sendToEffectStation(command: String): Result<Unit> {
        return sendToDevice(DeviceType.EFFECT_STATION, command)
    }

    /**
     * ActionDrive Motor1にコマンドを送信
     */
    suspend fun sendToActionDrive1(command: String): Result<Unit> {
        return sendToDevice(DeviceType.ACTION_DRIVE_1, command)
    }

    /**
     * ActionDrive Motor2にコマンドを送信
     */
    suspend fun sendToActionDrive2(command: String): Result<Unit> {
        return sendToDevice(DeviceType.ACTION_DRIVE_2, command)
    }

    /**
     * 両方のActionDriveにコマンドを送信
     */
    suspend fun sendToActionDriveBoth(command: String): Result<Unit> {
        val result1 = sendToActionDrive1(command)
        val result2 = sendToActionDrive2(command)
        
        return when {
            result1.isSuccess && result2.isSuccess -> Result.success(Unit)
            result1.isFailure && result2.isFailure -> result1
            result1.isFailure -> result1
            else -> result2
        }
    }

    /**
     * 特定デバイスタイプにコマンドを送信
     */
    suspend fun sendToDevice(deviceType: DeviceType, command: String): Result<Unit> {
        val connection = deviceManager.getConnectionByType(deviceType)
            ?: return Result.failure(Exception("${deviceType.displayName}が接続されていません"))
        
        return mutex.withLock {
            deviceManager.sendCommand(connection.address, command)
        }
    }

    /**
     * 全デバイスに停止コマンドを送信
     */
    suspend fun stopAll(): List<Result<Unit>> {
        val results = mutableListOf<Result<Unit>>()
        
        // EffectStation
        deviceManager.getConnectionByType(DeviceType.EFFECT_STATION)?.let { conn ->
            results.add(deviceManager.sendCommand(conn.address, "FAN,0"))
            results.add(deviceManager.sendCommand(conn.address, "MIST,0"))
            results.add(deviceManager.sendCommand(conn.address, "LED,11,0,0,0"))
        }
        
        // ActionDrive
        listOf(DeviceType.ACTION_DRIVE_1, DeviceType.ACTION_DRIVE_2).forEach { type ->
            deviceManager.getConnectionByType(type)?.let { conn ->
                results.add(deviceManager.sendCommand(conn.address, "OFF"))
            }
        }
        
        return results
    }
}
```

---

## 2.5 Hilt DIモジュール

### 2.5.1 di/BleModule.kt

```kotlin
package com.wildcard.fourd_at_home.di

import android.content.Context
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.BleScanner
import com.wildcard.fourd_at_home.ble.CommandSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideBleScanner(
        @ApplicationContext context: Context
    ): BleScanner {
        return BleScanner(context)
    }

    @Provides
    @Singleton
    fun provideBleDeviceManager(
        @ApplicationContext context: Context
    ): BleDeviceManager {
        return BleDeviceManager(context)
    }

    @Provides
    @Singleton
    fun provideCommandSender(
        deviceManager: BleDeviceManager
    ): CommandSender {
        return CommandSender(deviceManager)
    }
}
```

---

## 2.6 設定画面UI

### 2.6.1 ui/settings/SettingsViewModel.kt

```kotlin
package com.wildcard.fourd_at_home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildcard.fourd_at_home.ble.BleConnection
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.BleError
import com.wildcard.fourd_at_home.ble.BleScanner
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isScanning: Boolean = false,
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connections: Map<String, BleConnection> = emptyMap(),
    val error: BleError? = null,
    val hasPermissions: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleDeviceManager: BleDeviceManager
) : ViewModel() {

    private val _hasPermissions = MutableStateFlow(false)
    
    val uiState: StateFlow<SettingsUiState> = combine(
        bleScanner.isScanning,
        bleScanner.scannedDevices,
        bleDeviceManager.connections,
        bleScanner.error,
        _hasPermissions
    ) { isScanning, scannedDevices, connections, error, hasPermissions ->
        SettingsUiState(
            isScanning = isScanning,
            scannedDevices = scannedDevices.values.toList()
                .sortedByDescending { it.rssi },
            connections = connections,
            error = error,
            hasPermissions = hasPermissions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updatePermissionState(granted: Boolean) {
        _hasPermissions.value = granted
    }

    fun checkPermissions(): Boolean {
        val hasPermissions = bleScanner.hasRequiredPermissions()
        _hasPermissions.value = hasPermissions
        return hasPermissions
    }

    fun getRequiredPermissions(): Array<String> {
        return bleScanner.getRequiredPermissions()
    }

    fun isBluetoothEnabled(): Boolean {
        return bleScanner.isBluetoothEnabled()
    }

    fun startScan() {
        if (!bleScanner.hasRequiredPermissions()) {
            return
        }
        bleScanner.startScan()
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    fun toggleScan() {
        if (uiState.value.isScanning) {
            stopScan()
        } else {
            startScan()
        }
    }

    fun connect(device: ScannedDevice) {
        viewModelScope.launch {
            bleDeviceManager.connect(device)
        }
    }

    fun disconnect(address: String) {
        bleDeviceManager.disconnect(address)
    }

    fun getConnectionState(address: String): ConnectionState {
        return uiState.value.connections[address]?.state ?: ConnectionState.DISCONNECTED
    }

    fun clearError() {
        bleScanner.clearError()
    }
}
```

### 2.6.2 ui/settings/SettingsScreen.kt

```kotlin
package com.wildcard.fourd_at_home.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.ScannedDevice
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import com.wildcard.fourd_at_home.ui.theme.StatusConnecting
import com.wildcard.fourd_at_home.ui.theme.StatusDisconnected

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 権限リクエストランチャー
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        viewModel.updatePermissionState(allGranted)
        if (allGranted) {
            viewModel.startScan()
        }
    }

    // 初回権限チェック
    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左ペイン: デバイススキャン
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            // スキャンヘッダー
            ScanHeader(
                isScanning = uiState.isScanning,
                hasPermissions = uiState.hasPermissions,
                onToggleScan = {
                    if (uiState.hasPermissions) {
                        viewModel.toggleScan()
                    } else {
                        permissionLauncher.launch(viewModel.getRequiredPermissions())
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 権限警告
            if (!uiState.hasPermissions) {
                PermissionWarning(
                    onRequestPermission = {
                        permissionLauncher.launch(viewModel.getRequiredPermissions())
                    }
                )
            }

            // デバイスリスト
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.scannedDevices) { device ->
                    DeviceCard(
                        device = device,
                        connectionState = viewModel.getConnectionState(device.address),
                        onConnect = { viewModel.connect(device) },
                        onDisconnect = { viewModel.disconnect(device.address) }
                    )
                }
            }
        }

        // 右ペイン: 接続済みデバイス / マッピング（Phase 3で実装）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            Text(
                text = "接続済みデバイス",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val connectedDevices = uiState.connections.values
                .filter { it.state == ConnectionState.READY }
            
            if (connectedDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "接続されているデバイスはありません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(connectedDevices.toList()) { connection ->
                        ConnectedDeviceCard(connection = connection)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(
    isScanning: Boolean,
    hasPermissions: Boolean,
    onToggleScan: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isScanning) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "デバイス検索",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Button(
            onClick = onToggleScan,
            enabled = hasPermissions || !isScanning
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止")
            } else {
                Text("スキャン開始")
            }
        }
    }
}

@Composable
private fun PermissionWarning(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bluetooth権限が必要です",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            OutlinedButton(onClick = onRequestPermission) {
                Text("許可")
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val stateColor = when (connectionState) {
        ConnectionState.READY -> StatusConnected
        ConnectionState.CONNECTING, ConnectionState.CONNECTED, ConnectionState.DISCOVERING_SERVICES -> StatusConnecting
        ConnectionState.ERROR -> StatusDisconnected
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 接続状態インジケーター
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(stateColor, RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${device.deviceType.displayName} • ${device.signalStrength.emoji}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (connectionState) {
                ConnectionState.DISCONNECTED -> {
                    Button(onClick = onConnect) {
                        Text("接続")
                    }
                }
                ConnectionState.CONNECTING, ConnectionState.CONNECTED, ConnectionState.DISCOVERING_SERVICES -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("接続中...")
                    }
                }
                ConnectionState.READY -> {
                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusConnected
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothConnected,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("接続済み")
                    }
                }
                else -> {
                    Button(onClick = onConnect) {
                        Text("再接続")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    connection: com.wildcard.fourd_at_home.ble.BleConnection
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothConnected,
                contentDescription = null,
                tint = StatusConnected
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = connection.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = connection.deviceType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## 2.7 権限ハンドリング

AndroidManifest.xmlに権限を追加済み（Phase 1で設定）。

SettingsScreenでランタイム権限をリクエスト済み。

---

## ✅ Phase 2 完了チェックリスト

- [ ] BleScanner がデバイスをスキャンできる
- [ ] 4D_で始まるデバイスのみフィルタリングされる
- [ ] デバイスに接続・切断できる
- [ ] コマンドを送信できる
- [ ] 設定画面でスキャン・接続UIが動作する
- [ ] BLE権限のリクエストが動作する

---

## 📝 次のPhase

[Phase 3: EffectStation実装](./03_PHASE3_EFFECT_STATION.md) へ進む
