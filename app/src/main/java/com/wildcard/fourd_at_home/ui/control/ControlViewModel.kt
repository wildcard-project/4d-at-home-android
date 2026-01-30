package com.wildcard.fourd_at_home.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildcard.fourd_at_home.ble.BleConnection
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.CommandSender
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * エフェクト状態
 */
data class EffectState(
    val fanIntensity: Int = 0,
    val waterIntensity: Int = 0,
    val mistIntensity: Int = 0,
    val ledR: Int = 0,
    val ledG: Int = 0,
    val ledB: Int = 0,
    val ledBrightness: Int = 255,
    val motor1Intensity: Int = 0,
    val motor2Intensity: Int = 0
)

/**
 * 制御画面のUI状態
 */
data class ControlUiState(
    val effectState: EffectState = EffectState(),
    val isEffectStationConnected: Boolean = false,
    val isMotor1Connected: Boolean = false,
    val isMotor2Connected: Boolean = false,
    val lastError: String? = null,
    val isSending: Boolean = false
)

/**
 * 制御画面のViewModel
 */
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val deviceManager: BleDeviceManager,
    private val commandSender: CommandSender
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    // 接続状態を監視
    val connections: StateFlow<Map<String, BleConnection>> = deviceManager.connections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        observeConnections()
    }

    private fun observeConnections() {
        viewModelScope.launch {
            deviceManager.connections.collect { connections ->
                val esConnected = connections.values.any { 
                    it.deviceType == DeviceType.EFFECT_STATION && it.state == ConnectionState.READY 
                }
                val m1Connected = connections.values.any { 
                    it.deviceType == DeviceType.ACTION_DRIVE_1 && it.state == ConnectionState.READY 
                }
                val m2Connected = connections.values.any { 
                    it.deviceType == DeviceType.ACTION_DRIVE_2 && it.state == ConnectionState.READY 
                }
                
                _uiState.value = _uiState.value.copy(
                    isEffectStationConnected = esConnected,
                    isMotor1Connected = m1Connected,
                    isMotor2Connected = m2Connected
                )
            }
        }
    }

    // === ファン制御 ===
    
    fun setFanIntensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(fanIntensity = intensity)
        )
        sendFanCommand(intensity > 0)
    }

    private fun sendFanCommand(on: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendFanCommand(on)
            handleResult(result)
        }
    }

    // === 水噴射制御 ===
    
    fun setWaterIntensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(waterIntensity = intensity)
        )
        // 水噴射はワンショット
        if (intensity > 0) {
            sendSplashCommand()
        }
    }

    private fun sendSplashCommand() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendSplashCommand()
            handleResult(result)
        }
    }

    // === ミスト制御 ===
    
    fun setMistIntensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(mistIntensity = intensity)
        )
        sendMistCommand(intensity)
    }

    private fun sendMistCommand(intensity: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMistCommand(intensity)
            handleResult(result)
        }
    }

    // === LED制御 ===
    
    fun setLedColor(r: Int, g: Int, b: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(
                ledR = r,
                ledG = g,
                ledB = b
            )
        )
        sendLedCommand()
    }

    fun setLedBrightness(brightness: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(ledBrightness = brightness)
        )
        sendLedCommand()
    }

    private fun sendLedCommand() {
        val state = _uiState.value.effectState
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendLedCommand(
                state.ledR,
                state.ledG,
                state.ledB,
                state.ledBrightness
            )
            handleResult(result)
        }
    }

    // === プリセットLEDカラー ===
    
    fun setPresetColor(preset: LedPreset) {
        setLedColor(preset.r, preset.g, preset.b)
    }

    // === モーター制御 ===
    
    fun setMotor1Intensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(motor1Intensity = intensity)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMotor1Command(intensity)
            handleResult(result)
        }
    }

    fun setMotor2Intensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(motor2Intensity = intensity)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMotor2Command(intensity)
            handleResult(result)
        }
    }

    fun setBothMotorsIntensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(
                motor1Intensity = intensity,
                motor2Intensity = intensity
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendBothMotorsCommand(intensity)
            handleResult(result)
        }
    }

    // === 全停止 ===
    
    fun stopAllEffects() {
        _uiState.value = _uiState.value.copy(
            effectState = EffectState()
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendAllDevicesOff()
            handleResult(result)
        }
    }

    fun stopEffectStation() {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(
                fanIntensity = 0,
                waterIntensity = 0,
                mistIntensity = 0,
                ledR = 0,
                ledG = 0,
                ledB = 0
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendEffectStationAllOff()
            handleResult(result)
        }
    }

    fun stopMotors() {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(
                motor1Intensity = 0,
                motor2Intensity = 0
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendAllMotorsOff()
            handleResult(result)
        }
    }

    private fun handleResult(result: Result<Unit>) {
        _uiState.value = _uiState.value.copy(
            isSending = false,
            lastError = if (result.isFailure) {
                result.exceptionOrNull()?.message
            } else {
                null
            }
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(lastError = null)
    }
}

/**
 * LEDプリセットカラー
 */
enum class LedPreset(val displayName: String, val r: Int, val g: Int, val b: Int) {
    RED("レッド", 255, 0, 0),
    GREEN("グリーン", 0, 255, 0),
    BLUE("ブルー", 0, 0, 255),
    YELLOW("イエロー", 255, 255, 0),
    CYAN("シアン", 0, 255, 255),
    MAGENTA("マゼンタ", 255, 0, 255),
    WHITE("ホワイト", 255, 255, 255),
    WARM_WHITE("暖白色", 255, 200, 150),
    ORANGE("オレンジ", 255, 128, 0),
    PURPLE("パープル", 128, 0, 255)
}
