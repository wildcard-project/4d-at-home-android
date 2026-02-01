package com.wildcard.fourd_at_home.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildcard.fourd_at_home.ble.BleConnection
import com.wildcard.fourd_at_home.ble.BleConstants
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.BleError
import com.wildcard.fourd_at_home.ble.BleScanner
import com.wildcard.fourd_at_home.ble.CommandLogEntry
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.DeviceType
import com.wildcard.fourd_at_home.ble.ScanEvent
import com.wildcard.fourd_at_home.ble.ScanState
import com.wildcard.fourd_at_home.ble.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 設定画面のUI状態
 */
data class SettingsUiState(
    val scanState: ScanState = ScanState.IDLE,
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connections: Map<String, BleConnection> = emptyMap(),
    val commandLog: List<CommandLogEntry> = emptyList(),
    val error: BleError? = null,
    val showPermissionDialog: Boolean = false,
    val requiredPermissions: List<String> = emptyList(),
    val bluetoothEnabled: Boolean = true
)

/**
 * 設定画面のViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleScanner: BleScanner,
    private val bleDeviceManager: BleDeviceManager
) : ViewModel() {
    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // 接続状態を直接公開
    val connections: StateFlow<Map<String, BleConnection>> = bleDeviceManager.connections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // コマンドログを直接公開
    val commandLog: StateFlow<List<CommandLogEntry>> = bleDeviceManager.commandLog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null

    init {
        observeBleState()
    }

    private fun observeBleState() {
        // スキャン状態を監視
        viewModelScope.launch {
            bleScanner.scanState.collect { state ->
                _uiState.value = _uiState.value.copy(scanState = state)
            }
        }

        // スキャン済みデバイスを監視
        viewModelScope.launch {
            bleScanner.scannedDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(
                    scannedDevices = devices.values.toList()
                        .sortedByDescending { it.rssi }
                )
            }
        }

        // 接続状態を監視
        viewModelScope.launch {
            bleDeviceManager.connections.collect { connections ->
                _uiState.value = _uiState.value.copy(connections = connections)
            }
        }

        // コマンドログを監視
        viewModelScope.launch {
            bleDeviceManager.commandLog.collect { log ->
                _uiState.value = _uiState.value.copy(commandLog = log)
            }
        }

        // エラーを監視
        viewModelScope.launch {
            bleScanner.error.collect { error ->
                _uiState.value = _uiState.value.copy(error = error)
            }
        }
    }

    /**
     * 必要な権限のリストを取得
     */
    fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * 権限が付与されているかチェック
     */
    fun hasRequiredPermissions(): Boolean {
        return bleScanner.hasRequiredPermissions()
    }

    /**
     * Bluetoothが有効かチェック
     */
    fun isBluetoothEnabled(): Boolean {
        val enabled = bleScanner.isBluetoothEnabled()
        _uiState.value = _uiState.value.copy(bluetoothEnabled = enabled)
        return enabled
    }

    /**
     * スキャンを開始
     */
    fun startScan() {
        Log.d(TAG, "スキャン開始")

        // 権限チェック
        if (!hasRequiredPermissions()) {
            _uiState.value = _uiState.value.copy(
                showPermissionDialog = true,
                requiredPermissions = getRequiredPermissions()
            )
            return
        }

        // Bluetoothチェック
        if (!isBluetoothEnabled()) {
            _uiState.value = _uiState.value.copy(
                error = BleError.BluetoothDisabled()
            )
            return
        }

        // 既存のスキャンをキャンセル
        scanJob?.cancel()
        scanTimeoutJob?.cancel()

        scanJob = viewModelScope.launch {
            bleScanner.startScan().collect { event ->
                when (event) {
                    is ScanEvent.Started -> {
                        Log.d(TAG, "スキャン開始イベント")
                    }
                    is ScanEvent.DeviceFound -> {
                        Log.d(TAG, "デバイス発見: ${event.device.name}")
                    }
                    is ScanEvent.Error -> {
                        Log.e(TAG, "スキャンエラー: ${event.error.message}")
                        _uiState.value = _uiState.value.copy(error = event.error)
                    }
                }
            }
        }

        // タイムアウト
        scanTimeoutJob = viewModelScope.launch {
            delay(BleConstants.SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    /**
     * スキャンを停止
     */
    fun stopScan() {
        Log.d(TAG, "スキャン停止")
        scanJob?.cancel()
        scanTimeoutJob?.cancel()
        bleScanner.stopScan()
    }

    /**
     * デバイスに接続
     */
    fun connectDevice(device: ScannedDevice) {
        Log.d(TAG, "デバイス接続: ${device.name}")
        viewModelScope.launch {
            val result = bleDeviceManager.connect(device)
            if (result.isFailure) {
                Log.e(TAG, "接続失敗: ${result.exceptionOrNull()?.message}")
                _uiState.value = _uiState.value.copy(
                    error = BleError.ConnectionFailed(
                        result.exceptionOrNull()?.message ?: "接続に失敗しました"
                    )
                )
            }
        }
    }

    /**
     * デバイスを切断
     */
    fun disconnectDevice(address: String) {
        Log.d(TAG, "デバイス切断: $address")
        bleDeviceManager.disconnect(address)
    }

    /**
     * 全デバイスを切断
     */
    fun disconnectAll() {
        Log.d(TAG, "全デバイス切断")
        bleDeviceManager.disconnectAll()
    }

    /**
     * 権限ダイアログを閉じる
     */
    fun dismissPermissionDialog() {
        _uiState.value = _uiState.value.copy(showPermissionDialog = false)
    }

    /**
     * 権限が付与された後の処理
     */
    fun onPermissionsGranted() {
        _uiState.value = _uiState.value.copy(showPermissionDialog = false)
        startScan()
    }

    /**
     * エラーをクリア
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * コマンドログをクリア
     */
    fun clearCommandLog() {
        bleDeviceManager.clearCommandLog()
    }

    /**
     * スキャン済みデバイスをクリア
     */
    fun clearScannedDevices() {
        bleScanner.clearScannedDevices()
    }

    /**
     * 接続済みデバイス数を取得
     */
    fun getConnectedDeviceCount(): Int {
        return _uiState.value.connections.count { 
            it.value.state == ConnectionState.READY 
        }
    }

    /**
     * 特定のデバイスタイプが接続されているかチェック
     */
    fun isDeviceTypeConnected(type: DeviceType): Boolean {
        return _uiState.value.connections.values.any { 
            it.deviceType == type && it.state == ConnectionState.READY 
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        scanTimeoutJob?.cancel()
        bleScanner.stopScan()
    }
}
