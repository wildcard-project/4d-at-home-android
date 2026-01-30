package com.wildcard.fourd_at_home.playback

import android.util.Log
import com.wildcard.fourd_at_home.ble.CommandSender
import com.wildcard.fourd_at_home.ble.TimelineEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 再生同期エンジン
 * 動画再生位置に同期してエフェクトを発火
 */
@Singleton
class PlaybackSyncEngine @Inject constructor(
    private val commandSender: CommandSender
) {
    companion object {
        private const val TAG = "PlaybackSyncEngine"
        private const val SYNC_INTERVAL_MS = 16L  // ~60fps
        private const val LOOKAHEAD_MS = 50L      // 先読み時間
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var syncJob: Job? = null
    private var timeline: TimelineFile? = null
    private var scheduledEvents: MutableList<ScheduledEvent> = mutableListOf()
    
    private val _state = MutableStateFlow(PlaybackSyncState())
    val state: StateFlow<PlaybackSyncState> = _state.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    /**
     * タイムラインを読み込む
     */
    fun loadTimeline(timelineFile: TimelineFile) {
        Log.d(TAG, "タイムライン読み込み: ${timelineFile.title}")
        
        timeline = timelineFile
        scheduledEvents = timelineFile.events.map { 
            ScheduledEvent(it.time, it) 
        }.toMutableList()
        
        _state.value = PlaybackSyncState(
            isLoaded = true,
            title = timelineFile.title,
            totalDuration = timelineFile.duration,
            totalEvents = timelineFile.events.size
        )
    }

    /**
     * 再生を開始
     */
    fun start() {
        if (timeline == null) {
            Log.w(TAG, "タイムラインが読み込まれていません")
            return
        }
        
        Log.d(TAG, "同期再生開始")
        _state.value = _state.value.copy(isPlaying = true)
    }

    /**
     * 再生を一時停止
     */
    fun pause() {
        Log.d(TAG, "同期再生一時停止")
        _state.value = _state.value.copy(isPlaying = false)
    }

    /**
     * 再生を停止してリセット
     */
    fun stop() {
        Log.d(TAG, "同期再生停止")
        
        syncJob?.cancel()
        syncJob = null
        
        // 全エフェクト停止
        scope.launch {
            commandSender.sendAllDevicesOff()
        }
        
        // 状態リセット
        resetEvents()
        _currentPositionMs.value = 0
        _state.value = _state.value.copy(
            isPlaying = false,
            currentEventIndex = 0
        )
    }

    /**
     * 再生位置を更新（ExoPlayerからのコールバック）
     */
    fun updatePosition(positionMs: Long) {
        _currentPositionMs.value = positionMs
        
        if (!_state.value.isPlaying) return
        
        // この位置で発火すべきイベントを処理
        processEventsAtPosition(positionMs)
    }

    /**
     * シーク時の処理
     */
    fun onSeek(positionMs: Long) {
        Log.d(TAG, "シーク: ${positionMs}ms")
        
        _currentPositionMs.value = positionMs
        
        // シーク位置より前のイベントは実行済みにマーク
        scheduledEvents.forEach { scheduled ->
            scheduled.executed = scheduled.timestampMs < positionMs
        }
        
        // 現在位置のエフェクト状態を復元
        restoreStateAtPosition(positionMs)
        
        _state.value = _state.value.copy(
            currentEventIndex = scheduledEvents.count { it.executed }
        )
    }

    /**
     * 指定位置でのイベント処理
     */
    private fun processEventsAtPosition(positionMs: Long) {
        val eventsToExecute = scheduledEvents.filter { scheduled ->
            !scheduled.executed && 
            scheduled.timestampMs <= positionMs + LOOKAHEAD_MS
        }
        
        if (eventsToExecute.isEmpty()) return
        
        scope.launch {
            eventsToExecute.forEach { scheduled ->
                scheduled.executed = true
                executeEvent(scheduled.event)
            }
            
            _state.value = _state.value.copy(
                currentEventIndex = scheduledEvents.count { it.executed }
            )
        }
    }

    /**
     * イベントを実行
     */
    private suspend fun executeEvent(eventData: TimelineEventData) {
        Log.d(TAG, "イベント実行: ${eventData.type} @ ${eventData.time}ms")
        
        val event = convertToTimelineEvent(eventData)
        val result = commandSender.sendTimelineEvent(event)
        
        if (result.isFailure) {
            Log.e(TAG, "イベント実行失敗: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * JSONイベントをTimelineEventに変換
     */
    private fun convertToTimelineEvent(data: TimelineEventData): TimelineEvent {
        return when (data.type) {
            EventType.FAN -> TimelineEvent.Fan(
                timestampMs = data.time,
                intensity = data.params.intensity
            )
            EventType.WATER -> TimelineEvent.Water(
                timestampMs = data.time,
                intensity = data.params.intensity
            )
            EventType.MIST -> TimelineEvent.Mist(
                timestampMs = data.time,
                intensity = data.params.intensity
            )
            EventType.LED -> TimelineEvent.Led(
                timestampMs = data.time,
                r = data.params.r,
                g = data.params.g,
                b = data.params.b,
                brightness = data.params.brightness
            )
            EventType.VIBRATION -> TimelineEvent.Vibration(
                timestampMs = data.time,
                intensity = data.params.intensity,
                motor = data.params.motor
            )
            EventType.ALL_OFF -> TimelineEvent.AllOff(
                timestampMs = data.time
            )
            else -> TimelineEvent.AllOff(timestampMs = data.time)
        }
    }

    /**
     * 指定位置でのエフェクト状態を復元
     */
    private fun restoreStateAtPosition(positionMs: Long) {
        scope.launch {
            // まず全てOFF
            commandSender.sendAllDevicesOff()
            
            // 現在位置より前の最新状態を適用
            val latestStates = mutableMapOf<String, TimelineEventData>()
            
            scheduledEvents
                .filter { it.timestampMs <= positionMs }
                .forEach { scheduled ->
                    latestStates[scheduled.event.type] = scheduled.event
                }
            
            // 各エフェクトの最新状態を適用
            latestStates.values.forEach { eventData ->
                if (eventData.type != EventType.ALL_OFF) {
                    executeEvent(eventData)
                }
            }
        }
    }

    /**
     * イベント実行状態をリセット
     */
    private fun resetEvents() {
        scheduledEvents.forEach { it.executed = false }
    }

    /**
     * 次のイベントまでの時間を取得
     */
    fun getNextEventTime(): Long? {
        val currentPos = _currentPositionMs.value
        return scheduledEvents
            .filter { !it.executed && it.timestampMs > currentPos }
            .minByOrNull { it.timestampMs }
            ?.timestampMs
    }

    /**
     * 残りイベント数を取得
     */
    fun getRemainingEventCount(): Int {
        return scheduledEvents.count { !it.executed }
    }
}

/**
 * 再生同期状態
 */
data class PlaybackSyncState(
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val totalDuration: Long = 0,
    val totalEvents: Int = 0,
    val currentEventIndex: Int = 0
)
