package com.wildcard.fourd_at_home.playback

import android.util.Log
import com.wildcard.fourd_at_home.ble.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 再生同期エンジン（JSON_SPECIFICATION.md準拠）
 * 動画再生位置に同期してエフェクトを発火
 */
@Singleton
class PlaybackSyncEngine @Inject constructor(
    private val commandSender: CommandSender
) {
    companion object {
        private const val TAG = "PlaybackSyncEngine"
        private const val LOOKAHEAD_MS = 50L      // 先読み時間
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var syncJob: Job? = null
    private var timeline: TimelineFile? = null
    private var scheduledEvents: MutableList<ScheduledEvent> = mutableListOf()
    
    // 現在アクティブなエフェクト状態を追跡
    private val activeEffects = mutableMapOf<String, TimelineEventData>()
    
    private val _state = MutableStateFlow(PlaybackSyncState())
    val state: StateFlow<PlaybackSyncState> = _state.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
    
    private val _currentCaption = MutableStateFlow(CurrentCaption())
    val currentCaption: StateFlow<CurrentCaption> = _currentCaption.asStateFlow()

    /**
     * タイムラインを読み込む（JSON_SPECIFICATION.md準拠）
     */
    fun loadTimeline(timelineFile: TimelineFile) {
        Log.d(TAG, "タイムライン読み込み: ${timelineFile.events.size}イベント")
        
        timeline = timelineFile
        // 秒をミリ秒に変換してスケジュール
        scheduledEvents = timelineFile.events.map { 
            ScheduledEvent((it.t * 1000).toLong(), it) 
        }.toMutableList()
        
        val maxDuration = scheduledEvents.maxOfOrNull { it.timestampMs } ?: 0L
        
        _state.value = PlaybackSyncState(
            isLoaded = true,
            title = "",
            totalDuration = maxDuration,
            totalEvents = timelineFile.events.size
        )
        
        activeEffects.clear()
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
        
        // 一時停止時に全エフェクト停止
        scope.launch {
            commandSender.sendAllDevicesOff()
        }
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
        activeEffects.clear()
        _currentPositionMs.value = 0
        _currentCaption.value = CurrentCaption()
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
     * イベントを実行（JSON_SPECIFICATION.md準拠）
     */
    private suspend fun executeEvent(eventData: TimelineEventData) {
        Log.d(TAG, "イベント実行: ${eventData.action} ${eventData.effect ?: ""} @ ${eventData.t}s")
        
        when (eventData.action) {
            EventAction.CAPTION -> {
                eventData.text?.let { text ->
                    _currentCaption.value = CurrentCaption(
                        text = text,
                        timestamp = (eventData.t * 1000).toLong()
                    )
                }
            }
            
            EventAction.START -> {
                eventData.effect?.let { effect ->
                    eventData.mode?.let { mode ->
                        val key = "${effect.name}:$mode"
                        activeEffects[key] = eventData
                        executeEffectStart(effect, mode)
                    }
                }
            }
            
            EventAction.STOP -> {
                eventData.effect?.let { effect ->
                    eventData.mode?.let { mode ->
                        val key = "${effect.name}:$mode"
                        activeEffects.remove(key)
                        executeEffectStop(effect, mode)
                    }
                }
            }
            
            EventAction.SHOT -> {
                eventData.effect?.let { effect ->
                    eventData.mode?.let { mode ->
                        executeEffectShot(effect, mode)
                    }
                }
            }
        }
    }

    /**
     * エフェクト開始
     */
    private suspend fun executeEffectStart(effect: EffectType, mode: String) {
        when (effect) {
            EffectType.WIND -> {
                commandSender.sendFanCommand(true)
            }
            
            EffectType.MIST -> {
                commandSender.sendMistCommand(2)  // 継続モード
            }
            
            EffectType.COLOR -> {
                ColorMode.fromJsonMode(mode)?.let { colorMode ->
                    commandSender.sendLedColorCommand(
                        colorId = colorMode.ledColorId,
                        brightness = 2,  // 強
                        effect = 0,      // 点灯
                        transition = 0   // 即時
                    )
                }
            }
            
            EffectType.FLASH -> {
                FlashMode.fromJsonMode(mode)?.let { flashMode ->
                    // 白色で点滅
                    commandSender.sendLedColorCommand(
                        colorId = 10,  // 白
                        brightness = 2,
                        effect = flashMode.ledEffect,
                        transition = 0
                    )
                }
            }
            
            EffectType.VIBRATION -> {
                VibrationMode.fromJsonMode(mode)?.let { vibMode ->
                    // ESP32が理解できるコマンド名（WEAK, MEDIUM_WEAK等）に変換
                    val command = vibMode.esp32Command
                    when (vibMode.target) {
                        MotorTarget.MOTOR_1 -> {
                            commandSender.sendMotor1StringCommand(command)
                        }
                        MotorTarget.MOTOR_2 -> {
                            commandSender.sendMotor2StringCommand(command)
                        }
                        MotorTarget.BOTH -> {
                            commandSender.sendMotor1StringCommand(command)
                            commandSender.sendMotor2StringCommand(command)
                        }
                    }
                }
            }
            
            EffectType.WATER -> {
                // WATERはshotアクションのみ
            }
        }
    }

    /**
     * エフェクト停止
     */
    private suspend fun executeEffectStop(effect: EffectType, mode: String) {
        when (effect) {
            EffectType.WIND -> {
                commandSender.sendFanCommand(false)
            }
            
            EffectType.MIST -> {
                commandSender.sendMistCommand(0)  // OFF
            }
            
            EffectType.COLOR, EffectType.FLASH -> {
                commandSender.sendLedColorCommand(
                    colorId = 11,  // 消灯
                    brightness = 0,
                    effect = 0,
                    transition = 0
                )
            }
            
            EffectType.VIBRATION -> {
                VibrationMode.fromJsonMode(mode)?.let { vibMode ->
                    when (vibMode.target) {
                        MotorTarget.MOTOR_1 -> {
                            commandSender.sendMotor1StringCommand("OFF")
                        }
                        MotorTarget.MOTOR_2 -> {
                            commandSender.sendMotor2StringCommand("OFF")
                        }
                        MotorTarget.BOTH -> {
                            commandSender.sendMotor1StringCommand("OFF")
                            commandSender.sendMotor2StringCommand("OFF")
                        }
                    }
                }
            }
            
            EffectType.WATER -> {
                // WATERは停止なし
            }
        }
    }

    /**
     * ワンショットエフェクト
     */
    private suspend fun executeEffectShot(effect: EffectType, mode: String) {
        when (effect) {
            EffectType.WATER -> {
                commandSender.sendSplashCommand()
            }
            
            EffectType.MIST -> {
                commandSender.sendMistCommand(1)  // 一瞬モード
            }
            
            else -> {
                // 他のエフェクトはshotをサポートしない
            }
        }
    }

    /**
     * 指定位置でのエフェクト状態を復元
     */
    private fun restoreStateAtPosition(positionMs: Long) {
        scope.launch {
            // まず全てOFF
            commandSender.sendAllDevicesOff()
            activeEffects.clear()
            
            // 現在位置までのstart/stop/shotイベントを再生
            val eventsUpToNow = scheduledEvents
                .filter { it.timestampMs <= positionMs }
                .sortedBy { it.timestampMs }
            
            // アクティブなエフェクトを計算
            val currentActiveEffects = mutableMapOf<String, TimelineEventData>()
            
            eventsUpToNow.forEach { scheduled ->
                val event = scheduled.event
                when (event.action) {
                    EventAction.START -> {
                        event.effect?.let { effect ->
                            event.mode?.let { mode ->
                                currentActiveEffects["${effect.name}:$mode"] = event
                            }
                        }
                    }
                    EventAction.STOP -> {
                        event.effect?.let { effect ->
                            event.mode?.let { mode ->
                                currentActiveEffects.remove("${effect.name}:$mode")
                            }
                        }
                    }
                    EventAction.CAPTION -> {
                        event.text?.let { text ->
                            _currentCaption.value = CurrentCaption(
                                text = text,
                                timestamp = (event.t * 1000).toLong()
                            )
                        }
                    }
                    else -> {}
                }
            }
            
            // アクティブなエフェクトを適用
            currentActiveEffects.values.forEach { event ->
                event.effect?.let { effect ->
                    event.mode?.let { mode ->
                        executeEffectStart(effect, mode)
                    }
                }
            }
            
            activeEffects.clear()
            activeEffects.putAll(currentActiveEffects)
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
