package com.wildcard.fourd_at_home.playback

import android.util.Log
import com.wildcard.fourd_at_home.ble.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val commandSender: CommandSender,
    private val phoneEffectController: PhoneEffectController
) {
    companion object {
        private const val TAG = "PlaybackSyncEngine"
        // 250ms間隔のイベントに対応するため、200ms先読み
        // これによりBLE通信遅延(約50-100ms)を考慮して余裕を持って送信
        private const val LOOKAHEAD_MS = 200L
        // コマンド間の最小間隔（ESP32の処理時間を考慮）
        private const val MIN_COMMAND_INTERVAL_MS = 20L
        // SHOTエフェクトのUI表示時間（ミリ秒）
        private const val SHOT_DISPLAY_DURATION_MS = 800L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var syncJob: Job? = null
    private var timeline: TimelineFile? = null
    private var scheduledEvents: MutableList<ScheduledEvent> = mutableListOf()
    
    // 現在アクティブなエフェクト状態を追跡
    private val activeEffects = mutableMapOf<String, TimelineEventData>()
    
    // colorエフェクトがアクティブかどうかを追跡
    // colorがアクティブな間はflashを抑制する（同一LEDのため）
    private var isColorActive = false
    
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
        isColorActive = false
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
        Log.d(TAG, "一時停止 - 全デバイスに停止コマンド送信")
        scope.launch {
            val result = commandSender.sendAllDevicesOff()
            if (result.isSuccess) {
                Log.d(TAG, "全エフェクト停止コマンド送信成功")
            } else {
                Log.e(TAG, "全エフェクト停止コマンド送信失敗: ${result.exceptionOrNull()?.message}")
            }
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
        Log.d(TAG, "停止 - 全デバイスに停止コマンド送信")
        scope.launch {
            val result = commandSender.sendAllDevicesOff()
            if (result.isSuccess) {
                Log.d(TAG, "全エフェクト停止コマンド送信成功")
            } else {
                Log.e(TAG, "全エフェクト停止コマンド送信失敗: ${result.exceptionOrNull()?.message}")
            }
        }
        
        // スマホエフェクト停止
        phoneEffectController.stopAll()
        
        // 状態リセット
        resetEvents()
        activeEffects.clear()
        isColorActive = false
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
     * 
     * 250msごとのイベント間隔に対応するため、以下の最適化を実施:
     * 1. 同じ時刻のイベントは順序を保ちつつ効率的に処理
     * 2. STOP→START最適化でBLE通信を削減
     * 3. 異なるデバイスへのコマンドは並列送信
     * 
     * 処理順序:
     * 1. STOP - まず停止処理を行う
     * 2. CAPTION - キャプション更新（BLE不要）
     * 3. START/SHOT - エフェクト開始
     */
    private fun processEventsAtPosition(positionMs: Long) {
        val eventsToExecute = scheduledEvents.filter { scheduled ->
            !scheduled.executed && 
            scheduled.timestampMs <= positionMs + LOOKAHEAD_MS
        }
        
        if (eventsToExecute.isEmpty()) return
        
        scope.launch {
            // 同じ時刻のイベントをグループ化
            val eventsByTime = eventsToExecute.groupBy { it.timestampMs }
            
            eventsByTime.keys.sorted().forEach { timestamp ->
                val eventsAtTime = eventsByTime[timestamp] ?: return@forEach
                
                // イベントをアクション順にソート (STOP -> CAPTION -> START -> SHOT)
                val sortedEvents = eventsAtTime.sortedWith(
                    compareBy { scheduled ->
                        when (scheduled.event.action) {
                            EventAction.STOP -> 0
                            EventAction.CAPTION -> 1
                            EventAction.START -> 2
                            EventAction.SHOT -> 3
                        }
                    }
                )
                
                // 同じエフェクトのSTOP→START最適化を検出
                val optimizedEvents = optimizeStopStartEvents(sortedEvents)
                
                // バッチ処理: 同一時刻のイベントをまとめて処理
                // CAPTIONは即座に処理（BLE不要）、他はexecuteEventで処理
                optimizedEvents.forEach { (scheduled, skipStop) ->
                    scheduled.executed = true
                    if (!skipStop) {
                        executeEvent(scheduled.event)
                    } else {
                        Log.d(TAG, "STOP最適化によりスキップ: ${scheduled.event.effect} @ ${scheduled.event.t}s")
                    }
                }
                
                // 同一時刻のイベント処理後、最小間隔を空ける（ESP32の処理時間確保）
                if (eventsAtTime.size > 1) {
                    kotlinx.coroutines.delay(MIN_COMMAND_INTERVAL_MS)
                }
            }
            
            _state.value = _state.value.copy(
                currentEventIndex = scheduledEvents.count { it.executed }
            )
        }
    }
    
    /**
     * 同じ時刻で同じエフェクトのSTOP→STARTがある場合、STOPをスキップする最適化
     * これによりBLE通信回数を減らし、デバイス側でのタイミング問題を回避する
     * 
     * @return Pair<ScheduledEvent, Boolean> - Boolean=trueの場合はスキップ（実行しない）
     */
    private fun optimizeStopStartEvents(
        sortedEvents: List<ScheduledEvent>
    ): List<Pair<ScheduledEvent, Boolean>> {
        val result = mutableListOf<Pair<ScheduledEvent, Boolean>>()
        
        // STOPイベントを抽出
        val stopEvents = sortedEvents.filter { it.event.action == EventAction.STOP }
        // STARTイベントを抽出
        val startEvents = sortedEvents.filter { it.event.action == EventAction.START }
        
        // 同じエフェクトタイプのSTOP→STARTペアを検出
        val stopsToSkip = mutableSetOf<ScheduledEvent>()
        
        stopEvents.forEach { stopEvent ->
            val matchingStart = startEvents.find { startEvent ->
                startEvent.event.effect == stopEvent.event.effect
            }
            if (matchingStart != null) {
                // 同じエフェクトのSTOP→STARTがあるので、STOPはスキップ
                stopsToSkip.add(stopEvent)
                Log.d(TAG, "STOP→START最適化: ${stopEvent.event.effect} " +
                    "mode ${stopEvent.event.mode} → ${matchingStart.event.mode}")
            }
        }
        
        sortedEvents.forEach { scheduled ->
            val skipThis = stopsToSkip.contains(scheduled)
            result.add(scheduled to skipThis)
        }
        
        return result
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
                        updateActiveEffectsUI()
                        executeEffectStart(effect, mode)
                    }
                }
            }
            
            EventAction.STOP -> {
                eventData.effect?.let { effect ->
                    eventData.mode?.let { mode ->
                        val key = "${effect.name}:$mode"
                        activeEffects.remove(key)
                        updateActiveEffectsUI()
                        executeEffectStop(effect, mode)
                    }
                }
            }
            
            EventAction.SHOT -> {
                eventData.effect?.let { effect ->
                    eventData.mode?.let { mode ->
                        val key = "${effect.name}:$mode"
                        // SHOTでもUIに一時的に反映
                        activeEffects[key] = eventData
                        updateActiveEffectsUI()
                        
                        executeEffectShot(effect, mode)
                        
                        // 一定時間後にUIから削除（他のSTARTで上書きされていなければ）
                        scope.launch {
                            delay(SHOT_DISPLAY_DURATION_MS)
                            // まだ同じイベントがアクティブなら削除
                            if (activeEffects[key] == eventData) {
                                activeEffects.remove(key)
                                updateActiveEffectsUI()
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * activeEffectsをUIに反映
     */
    private fun updateActiveEffectsUI() {
        _state.value = _state.value.copy(
            activeEffects = activeEffects.keys.map { it.substringBefore(':') }.distinct()
        )
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
                MistMode.fromJsonMode(mode)?.let { mistMode ->
                    commandSender.sendMistCommand(mistMode.commandValue)
                } ?: run {
                    // フォールバック: 不明なモードは継続モード
                    commandSender.sendMistCommand(2)
                }
            }
            
            EffectType.COLOR -> {
                // colorアクティブフラグをセット（flashよりcolorを優先）
                isColorActive = true
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
                // colorがアクティブな間はflashを抑制（同一LEDのため）
                if (isColorActive) {
                    Log.d(TAG, "FLASH抑制: colorがアクティブ中 @ mode=$mode")
                    return
                }
                FlashMode.fromJsonMode(mode)?.let { flashMode ->
                    // 白色で点滅
                    commandSender.sendLedColorCommand(
                        colorId = 10,  // 白
                        brightness = 2,
                        effect = flashMode.ledEffect,
                        transition = 0
                    )
                    
                    // スマホフラッシュライト連動
                    phoneEffectController.startFlash(mode)
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
                            // 並列送信で両モーターに同時にコマンドを送信
                            commandSender.sendBothMotorsParallel(command)
                        }
                    }
                    
                    // スマホバイブレーション連動
                    val isHeartbeat = mode.equals("heartbeat", ignoreCase = true)
                    phoneEffectController.startVibration(mode, isHeartbeat)
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
            
            EffectType.COLOR -> {
                // colorアクティブフラグをリセット
                isColorActive = false
                commandSender.sendLedColorCommand(
                    colorId = 11,  // 消灯
                    brightness = 0,
                    effect = 0,
                    transition = 0
                )
            }
            
            EffectType.FLASH -> {
                // colorがアクティブな間はflashの停止も抑制（LEDはcolorの制御下）
                if (isColorActive) {
                    Log.d(TAG, "FLASH STOP抑制: colorがアクティブ中 @ mode=$mode")
                    return
                }
                commandSender.sendLedColorCommand(
                    colorId = 11,  // 消灯
                    brightness = 0,
                    effect = 0,
                    transition = 0
                )
                
                // スマホフラッシュライト停止
                phoneEffectController.stopFlash()
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
                            // 並列送信で両モーターを同時にOFF
                            commandSender.sendBothMotorsParallel("OFF")
                        }
                    }
                }
                
                // スマホバイブレーション停止
                phoneEffectController.stopVibration()
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
                // スマホバイブレーション（水しぶき感）
                phoneEffectController.shotVibration(300)
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
     * 
     * 同じ時刻のイベントはSTOP→STARTの順序で処理する
     */
    private fun restoreStateAtPosition(positionMs: Long) {
        scope.launch {
            // まず全てOFF
            commandSender.sendAllDevicesOff()
            activeEffects.clear()
            isColorActive = false
            
            // 現在位置までのstart/stop/shotイベントを再生
            val eventsUpToNow = scheduledEvents
                .filter { it.timestampMs <= positionMs }
            
            // 同じ時刻のイベントをグループ化し、時刻順に処理
            val eventsByTime = eventsUpToNow.groupBy { it.timestampMs }
            
            // アクティブなエフェクトを計算
            val currentActiveEffects = mutableMapOf<String, TimelineEventData>()
            
            eventsByTime.keys.sorted().forEach { timestamp ->
                val eventsAtTime = eventsByTime[timestamp] ?: return@forEach
                
                // 同じ時刻内でSTOP→STARTの順序で処理
                val sortedEvents = eventsAtTime.sortedWith(
                    compareBy { scheduled ->
                        when (scheduled.event.action) {
                            EventAction.STOP -> 0
                            EventAction.CAPTION -> 1
                            EventAction.START -> 2
                            EventAction.SHOT -> 3
                        }
                    }
                )
                
                sortedEvents.forEach { scheduled ->
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
            }
            
            // isColorActiveを復元（colorがアクティブかどうかを判定）
            isColorActive = currentActiveEffects.keys.any { it.startsWith("COLOR:") }
            
            // アクティブなエフェクトを適用（colorを先に適用してisColorActiveを正しく設定）
            val sortedEffects = currentActiveEffects.values.sortedBy { event ->
                if (event.effect == EffectType.COLOR) 0 else 1
            }
            sortedEffects.forEach { event ->
                event.effect?.let { effect ->
                    event.mode?.let { mode ->
                        executeEffectStart(effect, mode)
                    }
                }
            }
            
            activeEffects.clear()
            activeEffects.putAll(currentActiveEffects)
            
            // UIに反映
            _state.value = _state.value.copy(
                activeEffects = currentActiveEffects.keys.map { it.substringBefore(':') }.distinct()
            )
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
    val currentEventIndex: Int = 0,
    val activeEffects: List<String> = emptyList()
)
