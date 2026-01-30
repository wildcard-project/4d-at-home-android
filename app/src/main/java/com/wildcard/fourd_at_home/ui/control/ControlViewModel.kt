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
 * LED色プリセット（4DHOME_STATION_CONTROL.ino準拠）
 */
enum class LedColorPreset(val displayName: String, val colorId: Int) {
    PINK("ピンク", 0),
    RED("赤", 1),
    ORANGE("オレンジ", 2),
    YELLOW("黄色", 3),
    YELLOW_GREEN("黄緑", 4),
    GREEN("緑", 5),
    DARK_GREEN("深緑", 6),
    CYAN("水色", 7),
    BLUE("青", 8),
    PURPLE("紫", 9),
    WHITE("白", 10),
    OFF("消灯", 11)
}

/**
 * LED明るさ（4DHOME_STATION_CONTROL.ino準拠）
 */
enum class LedBrightnessLevel(val displayName: String, val value: Int) {
    OFF("OFF", 0),
    LOW("弱", 1),
    HIGH("強", 2)
}

/**
 * LEDエフェクト（4DHOME_STATION_CONTROL.ino準拠）
 */
enum class LedEffectMode(val displayName: String, val value: Int) {
    STEADY("点灯", 0),
    BLINK("点滅", 1),
    BREATHE("呼吸", 2)
}

/**
 * LEDトランジション（4DHOME_STATION_CONTROL.ino準拠）
 */
enum class LedTransitionMode(val displayName: String, val value: Int) {
    INSTANT("一瞬", 0),
    FADE("フェード", 1)
}

/**
 * ミストモード（4DHOME_STATION_CONTROL.ino準拠）
 */
enum class MistMode(val displayName: String, val value: Int) {
    OFF("OFF", 0),
    SHOT("一瞬", 1),
    CONTINUOUS("継続", 2)
}

/**
 * 振動モード（MQTT版互換）
 */
enum class VibrationLevel(val displayName: String, val commandName: String) {
    OFF("OFF", "OFF"),
    WEAK("弱", "WEAK"),
    MEDIUM_WEAK("中弱", "MEDIUM_WEAK"),
    MEDIUM_STRONG("中強", "MEDIUM_STRONG"),
    STRONG("強", "STRONG");
    
    companion object {
        // 基本強度モードのみ（パターン除く）
        val basicLevels = listOf(OFF, WEAK, MEDIUM_WEAK, MEDIUM_STRONG, STRONG)
    }
}

/**
 * 振動パターンモード（MQTT版互換）
 */
enum class VibrationPattern(val displayName: String, val commandName: String, val icon: String) {
    HEARTBEAT("心拍", "HEARTBEAT", "❤️"),
    RUMBLE_FAST("高速振動", "RUMBLE_FAST", "⚡"),
    RUMBLE_SLOW("低速振動", "RUMBLE_SLOW", "🌊")
}

/**
 * エフェクト状態
 */
data class EffectState(
    // EffectStation
    val fanOn: Boolean = false,
    val mistMode: MistMode = MistMode.OFF,
    val ledColor: LedColorPreset = LedColorPreset.OFF,
    val ledBrightness: LedBrightnessLevel = LedBrightnessLevel.OFF,
    val ledEffect: LedEffectMode = LedEffectMode.STEADY,
    val ledTransition: LedTransitionMode = LedTransitionMode.INSTANT,
    // ActionDrive
    val motor1Level: VibrationLevel = VibrationLevel.OFF,
    val motor2Level: VibrationLevel = VibrationLevel.OFF
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

    // ===============================
    // EffectStation制御
    // ===============================

    // === ファン制御 ===
    fun toggleFan() {
        val newState = !_uiState.value.effectState.fanOn
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(fanOn = newState)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendFanCommand(newState)
            handleResult(result)
        }
    }

    fun setFan(on: Boolean) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(fanOn = on)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendFanCommand(on)
            handleResult(result)
        }
    }

    // === 水噴射制御 ===
    fun triggerSplash() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendSplashCommand()
            handleResult(result)
        }
    }

    // === ミスト制御 ===
    fun setMistMode(mode: MistMode) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(mistMode = mode)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMistCommand(mode.value)
            handleResult(result)
        }
    }

    // === LED制御 ===
    fun setLedColor(color: LedColorPreset) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(ledColor = color)
        )
        sendLedCommand()
    }

    fun setLedBrightness(brightness: LedBrightnessLevel) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(ledBrightness = brightness)
        )
        sendLedCommand()
    }

    fun setLedEffect(effect: LedEffectMode) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(ledEffect = effect)
        )
        sendLedCommand()
    }

    fun setLedTransition(transition: LedTransitionMode) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(ledTransition = transition)
        )
        sendLedCommand()
    }

    private fun sendLedCommand() {
        val state = _uiState.value.effectState
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendLedColorCommand(
                colorId = state.ledColor.colorId,
                brightness = state.ledBrightness.value,
                effect = state.ledEffect.value,
                transition = state.ledTransition.value
            )
            handleResult(result)
        }
    }

    fun ledOff() {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(
                ledColor = LedColorPreset.OFF,
                ledBrightness = LedBrightnessLevel.OFF
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendLedColorCommand(11, 0, 0, 0)
            handleResult(result)
        }
    }

    // ===============================
    // ActionDrive制御
    // ===============================

    fun setMotor1Level(level: VibrationLevel) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(motor1Level = level)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMotor1Command(level.commandName)
            handleResult(result)
        }
    }

    fun setMotor2Level(level: VibrationLevel) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(motor2Level = level)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMotor2Command(level.commandName)
            handleResult(result)
        }
    }

    fun setBothMotorsLevel(level: VibrationLevel) {
        _uiState.value = _uiState.value.copy(
            effectState = _uiState.value.effectState.copy(
                motor1Level = level,
                motor2Level = level
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendBothMotorsCommand(level.commandName)
            handleResult(result)
        }
    }

    /**
     * パターンモードをMotor1に送信
     */
    fun sendMotor1Pattern(pattern: VibrationPattern) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMotor1Command(pattern.commandName)
            handleResult(result)
        }
    }

    /**
     * パターンモードをMotor2に送信
     */
    fun sendMotor2Pattern(pattern: VibrationPattern) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendMotor2Command(pattern.commandName)
            handleResult(result)
        }
    }

    /**
     * パターンモードを両モーターに送信
     */
    fun sendBothMotorsPattern(pattern: VibrationPattern) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendBothMotorsCommand(pattern.commandName)
            handleResult(result)
        }
    }

    // ===============================
    // 全停止
    // ===============================

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
                fanOn = false,
                mistMode = MistMode.OFF,
                ledColor = LedColorPreset.OFF,
                ledBrightness = LedBrightnessLevel.OFF
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
                motor1Level = VibrationLevel.OFF,
                motor2Level = VibrationLevel.OFF
            )
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val result = commandSender.sendAllMotorsOff()
            handleResult(result)
        }
    }

    // ===============================
    // ユーティリティ
    // ===============================

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
