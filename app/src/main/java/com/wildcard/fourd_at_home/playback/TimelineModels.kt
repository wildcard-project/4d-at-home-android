package com.wildcard.fourd_at_home.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * タイムラインファイルのルートモデル
 */
@Serializable
data class TimelineFile(
    val version: String = "1.0",
    val title: String = "",
    val duration: Long = 0,  // ミリ秒
    val events: List<TimelineEventData> = emptyList()
)

/**
 * タイムラインイベントデータ（JSON用）
 */
@Serializable
data class TimelineEventData(
    val time: Long,  // ミリ秒
    val type: String,
    val params: EventParams = EventParams()
)

/**
 * イベントパラメータ
 */
@Serializable
data class EventParams(
    // 共通
    val intensity: Int = 0,
    val duration: Long = 0,
    
    // LED用
    val r: Int = 0,
    val g: Int = 0,
    val b: Int = 0,
    val brightness: Int = 255,
    
    // 振動用
    val motor: Int = 0  // 0=both, 1=motor1, 2=motor2
)

/**
 * イベントタイプ定義
 */
object EventType {
    const val FAN = "fan"
    const val WATER = "water"
    const val MIST = "mist"
    const val LED = "led"
    const val VIBRATION = "vibration"
    const val ALL_OFF = "all_off"
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
