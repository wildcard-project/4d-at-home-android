package com.wildcard.fourd_at_home.ble

import android.Manifest
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLEスキャン状態
 */
enum class ScanState {
    IDLE,
    SCANNING,
    STOPPED,
    ERROR
}

/**
 * BLEスキャナー
 * デバイスのスキャンを管理する
 */
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
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    val scannedDevices: StateFlow<Map<String, ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _error = MutableStateFlow<BleError?>(null)
    val error: StateFlow<BleError?> = _error.asStateFlow()

    private var currentScanCallback: ScanCallback? = null

    /**
     * Bluetoothが利用可能かチェック
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * BLE権限があるかチェック
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
     * スキャンを開始し、結果をFlowで返す
     */
    fun startScan(): Flow<ScanEvent> = callbackFlow {
        Log.d(TAG, "スキャン開始リクエスト")

        // 事前チェック
        if (!isBluetoothEnabled()) {
            val error = BleError.BluetoothDisabled()
            _error.value = error
            _scanState.value = ScanState.ERROR
            trySend(ScanEvent.Error(error))
            close()
            return@callbackFlow
        }

        if (!hasRequiredPermissions()) {
            val error = BleError.PermissionDenied()
            _error.value = error
            _scanState.value = ScanState.ERROR
            trySend(ScanEvent.Error(error))
            close()
            return@callbackFlow
        }

        val scanner = bleScanner
        if (scanner == null) {
            val error = BleError.ScanFailed("スキャナーが利用できません", -1)
            _error.value = error
            _scanState.value = ScanState.ERROR
            trySend(ScanEvent.Error(error))
            close()
            return@callbackFlow
        }

        // スキャン中の場合は一度停止
        stopScanInternal()

        // スキャン結果をクリア
        _scannedDevices.value = emptyMap()
        _error.value = null

        // スキャンコールバック
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)?.let { device ->
                    trySend(ScanEvent.DeviceFound(device))
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    handleScanResult(result)?.let { device ->
                        trySend(ScanEvent.DeviceFound(device))
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "スキャン失敗: errorCode=$errorCode")
                val error = BleError.ScanFailed(
                    message = getScanFailureReason(errorCode),
                    errorCode = errorCode
                )
                _error.value = error
                _scanState.value = ScanState.ERROR
                trySend(ScanEvent.Error(error))
            }
        }

        currentScanCallback = callback

        // スキャンフィルターとセッティング
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
            _scanState.value = ScanState.SCANNING
            Log.d(TAG, "スキャン開始成功")
            trySend(ScanEvent.Started)
        } catch (e: SecurityException) {
            Log.e(TAG, "スキャン開始失敗 (権限エラー)", e)
            val error = BleError.PermissionDenied()
            _error.value = error
            _scanState.value = ScanState.ERROR
            trySend(ScanEvent.Error(error))
        }

        awaitClose {
            stopScanInternal()
        }
    }

    /**
     * スキャンを停止
     */
    fun stopScan() {
        Log.d(TAG, "スキャン停止リクエスト")
        stopScanInternal()
        _scanState.value = ScanState.STOPPED
    }

    private fun stopScanInternal() {
        currentScanCallback?.let { callback ->
            try {
                bleScanner?.stopScan(callback)
                Log.d(TAG, "スキャン停止成功")
            } catch (e: SecurityException) {
                Log.e(TAG, "スキャン停止失敗 (権限エラー)", e)
            }
            currentScanCallback = null
        }
    }

    private fun handleScanResult(result: ScanResult): ScannedDevice? {
        val device = result.device
        val name: String
        try {
            name = device.name ?: return null
        } catch (e: SecurityException) {
            return null
        }

        // デバイス名パターンチェック
        if (!BleConstants.DEVICE_NAME_PATTERN.matches(name)) {
            return null
        }

        val scannedDevice = ScannedDevice(
            device = device,
            name = name,
            address = device.address,
            rssi = result.rssi,
            deviceType = DeviceType.fromDeviceName(name)
        )

        // リストに追加/更新
        _scannedDevices.value = _scannedDevices.value.toMutableMap().apply {
            put(device.address, scannedDevice)
        }

        Log.d(TAG, "デバイス発見: $name (${device.address}), RSSI=${result.rssi}")
        return scannedDevice
    }

    private fun getScanFailureReason(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "既にスキャン中です"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "アプリ登録に失敗しました"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "この機能はサポートされていません"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "内部エラーが発生しました"
            else -> "不明なエラー (code: $errorCode)"
        }
    }

    /**
     * スキャン済みデバイスをクリア
     */
    fun clearScannedDevices() {
        _scannedDevices.value = emptyMap()
    }
}

/**
 * スキャンイベント
 */
sealed class ScanEvent {
    data object Started : ScanEvent()
    data class DeviceFound(val device: ScannedDevice) : ScanEvent()
    data class Error(val error: BleError) : ScanEvent()
}
