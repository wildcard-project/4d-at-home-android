package com.wildcard.fourd_at_home.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * タイムラインファイルのルートモデル（JSON_SPECIFICATION.md準拠）
 */
@Serializable
data class TimelineFile(
    val events: List<TimelineEventData> = emptyList()
)

/**
 * タイムラインイベントデータ（JSON_SPECIFICATION.md準拠）
 */
@Serializable
data class TimelineEventData(
    val t: Double,                                    // 時刻（秒）
    val action: EventAction,                          // start/stop/shot/caption
    val effect: EffectType? = null,                   // 効果タイプ
    val mode: String? = null,                         // モード
    val text: String? = null                          // キャプション用
)

/**
 * アクションタイプ（JSON_SPECIFICATION.md準拠）
 */
@Serializable
enum class EventAction {
    @SerialName("start") START,
    @SerialName("stop") STOP,
    @SerialName("shot") SHOT,
    @SerialName("caption") CAPTION
}

/**
 * 効果タイプ（JSON_SPECIFICATION.md準拠）
 */
@Serializable
enum class EffectType {
    @SerialName("vibration") VIBRATION,
    @SerialName("flash") FLASH,
    @SerialName("color") COLOR,
    @SerialName("water") WATER,
    @SerialName("wind") WIND,
    @SerialName("mist") MIST
}

/**
 * 振動モード（JSON_SPECIFICATION.md準拠）
 */
enum class VibrationMode(
    val jsonMode: String,
    val target: MotorTarget,
    val intensity: Int,
    val esp32Command: String  // ESP32が期待するコマンド名
) {
    // 上（背中）のみ - Motor1
    UP_WEAK("up_weak", MotorTarget.MOTOR_1, 64, "WEAK"),
    UP_MID_WEAK("up_mid_weak", MotorTarget.MOTOR_1, 128, "MEDIUM_WEAK"),
    UP_MID_STRONG("up_mid_strong", MotorTarget.MOTOR_1, 192, "MEDIUM_STRONG"),
    UP_STRONG("up_strong", MotorTarget.MOTOR_1, 255, "STRONG"),
    
    // 下（お尻）のみ - Motor2
    DOWN_WEAK("down_weak", MotorTarget.MOTOR_2, 64, "WEAK"),
    DOWN_MID_WEAK("down_mid_weak", MotorTarget.MOTOR_2, 128, "MEDIUM_WEAK"),
    DOWN_MID_STRONG("down_mid_strong", MotorTarget.MOTOR_2, 192, "MEDIUM_STRONG"),
    DOWN_STRONG("down_strong", MotorTarget.MOTOR_2, 255, "STRONG"),
    
    // 上下同時 - 両方
    UP_DOWN_WEAK("up_down_weak", MotorTarget.BOTH, 64, "WEAK"),
    UP_DOWN_MID_WEAK("up_down_mid_weak", MotorTarget.BOTH, 128, "MEDIUM_WEAK"),
    UP_DOWN_MID_STRONG("up_down_mid_strong", MotorTarget.BOTH, 192, "MEDIUM_STRONG"),
    UP_DOWN_STRONG("up_down_strong", MotorTarget.BOTH, 255, "STRONG"),
    
    // 特殊
    HEARTBEAT("heartbeat", MotorTarget.BOTH, 200, "HEARTBEAT");
    
    companion object {
        fun fromJsonMode(mode: String): VibrationMode? = 
            entries.find { it.jsonMode == mode }
    }
}

/**
 * フラッシュモード（4DHOME_STATION_CONTROL.ino準拠）
 * effect: 0=点灯, 1=点滅, 2=呼吸
 */
enum class FlashMode(val jsonMode: String, val ledEffect: Int) {
    STEADY("steady", 0),         // 点灯
    BLINK("blink", 1),           // 点滅
    SLOW_BLINK("slow_blink", 1), // 点滅（互換性のため維持）
    BREATHE("breathe", 2),       // 呼吸
    FAST_BLINK("fast_blink", 1); // 点滅（互換性のため維持、実際は通常点滅）
    
    companion object {
        fun fromJsonMode(mode: String): FlashMode? = 
            entries.find { it.jsonMode == mode }
    }
}

/**
 * カラーモード（4DHOME_STATION_CONTROL.ino準拠）
 * RGBW LED対応（Wチャンネルは白のみ使用）
 */
enum class ColorMode(val jsonMode: String, val ledColorId: Int, val r: Int, val g: Int, val b: Int) {
    PINK("pink", 0, 255, 20, 100),
    RED("red", 1, 255, 0, 0),
    ORANGE("orange", 2, 255, 100, 0),
    YELLOW("yellow", 3, 255, 255, 0),
    YELLOW_GREEN("yellow_green", 4, 150, 255, 0),
    GREEN("green", 5, 0, 255, 0),
    DARK_GREEN("dark_green", 6, 0, 100, 0),
    CYAN("cyan", 7, 0, 255, 255),
    BLUE("blue", 8, 0, 0, 255),
    PURPLE("purple", 9, 150, 0, 255),
    WHITE("white", 10, 255, 255, 255),
    OFF("off", 11, 0, 0, 0);
    
    companion object {
        fun fromJsonMode(mode: String): ColorMode? = 
            entries.find { it.jsonMode == mode }
    }
}

/**
 * ミストモード（ESP32 MIST制御対応）
 * ESP32コマンド: "MIST,0" (OFF), "MIST,1" (一瞬/burst), "MIST,2" (継続/stream)
 */
enum class MistMode(val jsonMode: String, val commandValue: Int, val description: String) {
    BURST("burst", 1, "一瞬噴射（自動OFF）"),
    STREAM("stream", 2, "継続噴射"),
    OFF("off", 0, "停止");
    
    companion object {
        fun fromJsonMode(mode: String): MistMode? = 
            entries.find { it.jsonMode == mode }
    }
}

/**
 * モーター対象
 */
enum class MotorTarget {
    MOTOR_1,    // 背中 (up)
    MOTOR_2,    // お尻 (down)
    BOTH        // 両方 (up_down)
}

/**
 * スケジュール済みイベント（再生用）
 */
data class ScheduledEvent(
    val timestampMs: Long,
    val event: TimelineEventData,
    var executed: Boolean = false
)

/**
 * タイムラインの状態
 */
data class TimelineState(
    val isLoaded: Boolean = false,
    val title: String = "",
    val totalDuration: Long = 0,
    val totalEvents: Int = 0,
    val currentEventIndex: Int = 0
)

/**
 * 現在のキャプション
 */
data class CurrentCaption(
    val text: String = "",
    val timestamp: Long = 0
)
