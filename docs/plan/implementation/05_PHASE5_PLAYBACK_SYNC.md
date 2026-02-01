# Phase 5: 再生同期エンジン

**期間目安**: 4-5日  
**前提条件**: Phase 3, 4 完了  
**優先度**: 高

---

## 📋 タスク一覧

| # | タスク | 優先度 | 完了条件 |
|:-:|:-------|:------:|:---------|
| 5.1 | ExoPlayer統合 | 必須 | 動画再生・コントロール動作 |
| 5.2 | JSONタイムライン定義 | 必須 | Web版互換フォーマット対応 |
| 5.3 | タイムラインパーサー | 必須 | JSONパース・検証動作 |
| 5.4 | 同期エンジン | 必須 | 再生位置とエフェクト同期 |
| 5.5 | 再生画面UI | 必須 | 動画 + エフェクト表示 |
| 5.6 | 動作テスト | 必須 | 同期動作確認 |

---

## 5.1 ExoPlayer統合

### 5.1.1 依存関係 (Phase 1で追加済み)

```kotlin
// gradle/libs.versions.toml
[versions]
media3 = "1.3.1"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
```

### 5.1.2 domain/player/VideoPlayer.kt

```kotlin
package com.wildcard.fourd_at_home.domain.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 再生状態
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f
) {
    val progressPercent: Float
        get() = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
}

@Singleton
class VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
    
    /**
     * プレイヤーを初期化
     */
    @OptIn(UnstableApi::class)
    fun initialize(): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!
        
        exoPlayer = ExoPlayer.Builder(context)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateState()
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState()
                        if (isPlaying) {
                            startPositionUpdates()
                        } else {
                            stopPositionUpdates()
                        }
                    }
                })
            }
        
        return exoPlayer!!
    }
    
    /**
     * 動画をセット
     */
    fun setMediaItem(uri: String) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
    }
    
    /**
     * アセット内の動画をセット
     */
    fun setAssetVideo(assetPath: String) {
        val uri = "asset:///$assetPath"
        setMediaItem(uri)
    }
    
    /**
     * 再生開始
     */
    fun play() {
        exoPlayer?.play()
    }
    
    /**
     * 一時停止
     */
    fun pause() {
        exoPlayer?.pause()
    }
    
    /**
     * シーク
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }
    
    /**
     * 再生速度変更
     */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        updateState()
    }
    
    /**
     * 現在位置を取得
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
    
    /**
     * 動画の長さを取得
     */
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }
    
    /**
     * プレイヤーを取得 (Compose用)
     */
    fun getPlayer(): ExoPlayer? = exoPlayer
    
    /**
     * リリース
     */
    fun release() {
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    private fun updateState() {
        _playbackState.value = PlaybackState(
            isPlaying = exoPlayer?.isPlaying ?: false,
            currentPositionMs = exoPlayer?.currentPosition ?: 0,
            durationMs = exoPlayer?.duration ?: 0,
            isBuffering = exoPlayer?.playbackState == Player.STATE_BUFFERING,
            playbackSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f
        )
    }
    
    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = exoPlayer?.currentPosition ?: 0L
                updateState()
                delay(16) // 約60fps
            }
        }
    }
    
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
}
```

---

## 5.2 JSONタイムライン定義

### 5.2.1 JSON_SPECIFICATION.md 準拠フォーマット

**注意**: JSON_SPECIFICATION.mdに完全準拠したイベントベースの形式を使用します。

```json
{
  "events": [
    {
      "t": 0.0,
      "action": "caption",
      "text": "シーンの説明文"
    },
    {
      "t": 5.0,
      "action": "start",
      "effect": "wind",
      "mode": "burst"
    },
    {
      "t": 10.0,
      "action": "stop",
      "effect": "wind",
      "mode": "burst"
    },
    {
      "t": 12.0,
      "action": "shot",
      "effect": "water",
      "mode": "burst"
    },
    {
      "t": 15.0,
      "action": "start",
      "effect": "mist",
      "mode": "burst"
    },
    {
      "t": 20.0,
      "action": "stop",
      "effect": "mist",
      "mode": "burst"
    },
    {
      "t": 25.0,
      "action": "start",
      "effect": "color",
      "mode": "red"
    },
    {
      "t": 30.0,
      "action": "stop",
      "effect": "color",
      "mode": "red"
    },
    {
      "t": 30.0,
      "action": "start",
      "effect": "flash",
      "mode": "fast_blink"
    },
    {
      "t": 31.0,
      "action": "stop",
      "effect": "flash",
      "mode": "fast_blink"
    },
    {
      "t": 35.0,
      "action": "start",
      "effect": "vibration",
      "mode": "up_down_strong"
    },
    {
      "t": 38.0,
      "action": "stop",
      "effect": "vibration",
      "mode": "up_down_strong"
    },
    {
      "t": 40.0,
      "action": "start",
      "effect": "vibration",
      "mode": "heartbeat"
    },
    {
      "t": 45.0,
      "action": "stop",
      "effect": "vibration",
      "mode": "heartbeat"
    }
  ]
}
```

### 5.2.2 エフェクトとモードの対応表

| effect | mode | 対象デバイス | 説明 |
|:-------|:-----|:-------------|:-----|
| `vibration` | `up_weak` / `up_mid_weak` / `up_mid_strong` / `up_strong` | Motor1 | 背中振動 |
| `vibration` | `down_weak` / `down_mid_weak` / `down_mid_strong` / `down_strong` | Motor2 | お尻振動 |
| `vibration` | `up_down_weak` / `up_down_mid_weak` / `up_down_mid_strong` / `up_down_strong` | 両方 | 上下同時 |
| `vibration` | `heartbeat` | 両方 | 心拍パターン |
| `flash` | `steady` / `slow_blink` / `fast_blink` | EffectStation | 閃光効果 |
| `color` | `red` / `green` / `blue` / `yellow` / `cyan` / `purple` | EffectStation | 環境照明 |
| `water` | `burst` | EffectStation | 水しぶき（shot専用） |
| `wind` | `burst` | EffectStation | 風 |
| `mist` | `burst` / `stream` | EffectStation | ミスト（burst:一瞬 / stream:継続） |

### 5.2.3 domain/model/Timeline.kt

```kotlin
package com.wildcard.fourd_at_home.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * タイムラインルート（JSON_SPECIFICATION.md準拠）
 */
@Serializable
data class Timeline(
    val events: List<TimelineEvent>
)

/**
 * タイムラインイベント
 */
@Serializable
data class TimelineEvent(
    val t: Double,                    // 時刻（秒）
    val action: EventAction,          // start/stop/shot/caption
    val effect: EffectType? = null,   // 効果タイプ
    val mode: String? = null,         // モード
    val text: String? = null          // キャプション用
)

/**
 * アクションタイプ
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
    @SerialName("mist") MIST  // 新規追加
}

/**
 * 振動モード（JSON_SPECIFICATION.md準拠）
 */
enum class VibrationMode(val jsonMode: String, val target: MotorTarget) {
    // 上（背中）のみ - Motor1
    UP_WEAK("up_weak", MotorTarget.MOTOR_1),
    UP_MID_WEAK("up_mid_weak", MotorTarget.MOTOR_1),
    UP_MID_STRONG("up_mid_strong", MotorTarget.MOTOR_1),
    UP_STRONG("up_strong", MotorTarget.MOTOR_1),
    
    // 下（お尻）のみ - Motor2
    DOWN_WEAK("down_weak", MotorTarget.MOTOR_2),
    DOWN_MID_WEAK("down_mid_weak", MotorTarget.MOTOR_2),
    DOWN_MID_STRONG("down_mid_strong", MotorTarget.MOTOR_2),
    DOWN_STRONG("down_strong", MotorTarget.MOTOR_2),
    
    // 上下同時 - 両方
    UP_DOWN_WEAK("up_down_weak", MotorTarget.BOTH),
    UP_DOWN_MID_WEAK("up_down_mid_weak", MotorTarget.BOTH),
    UP_DOWN_MID_STRONG("up_down_mid_strong", MotorTarget.BOTH),
    UP_DOWN_STRONG("up_down_strong", MotorTarget.BOTH),
    
    // 特殊
    HEARTBEAT("heartbeat", MotorTarget.BOTH);
    
    companion object {
        fun fromJsonMode(mode: String): VibrationMode? = 
            entries.find { it.jsonMode == mode }
    }
}

/**
 * フラッシュモード（JSON_SPECIFICATION.md準拠）
 */
enum class FlashMode(val jsonMode: String) {
    STEADY("steady"),
    SLOW_BLINK("slow_blink"),
    FAST_BLINK("fast_blink");
    
    companion object {
        fun fromJsonMode(mode: String): FlashMode? = 
            entries.find { it.jsonMode == mode }
    }
}

/**
 * カラーモード（JSON_SPECIFICATION.md準拠）
 */
enum class ColorMode(val jsonMode: String, val ledColorId: Int) {
    RED("red", 1),
    GREEN("green", 5),
    BLUE("blue", 8),
    YELLOW("yellow", 3),
    CYAN("cyan", 7),
    PURPLE("purple", 9);
    
    companion object {
        fun fromJsonMode(mode: String): ColorMode? = 
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
 * タイムライン検証結果
 */
data class TimelineValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * 処理済みイベント（内部用）
 * start/stopをペアにして開始・終了時刻を持つ形式
 */
data class ProcessedEffect(
    val effect: EffectType,
    val mode: String,
    val startTimeSec: Double,
    val endTimeSec: Double?  // nullの場合は動画終了まで
)
```

---

## 5.3 タイムラインパーサー

### 5.3.1 domain/timeline/TimelineParser.kt

```kotlin
package com.wildcard.fourd_at_home.domain.timeline

import android.content.Context
import com.wildcard.fourd_at_home.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * JSON文字列からパース
     */
    fun parse(jsonString: String): Result<Timeline> {
        return try {
            val timeline = json.decodeFromString<Timeline>(jsonString)
            Result.success(timeline)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * アセットからパース
     */
    fun parseFromAsset(assetPath: String): Result<Timeline> {
        return try {
            val jsonString = context.assets.open(assetPath)
                .bufferedReader()
                .use { it.readText() }
            parse(jsonString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * ファイルからパース
     */
    fun parseFromFile(file: File): Result<Timeline> {
        return try {
            val jsonString = file.readText()
            parse(jsonString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * タイムラインを検証（JSON_SPECIFICATION.md準拠）
     */
    fun validate(timeline: Timeline): TimelineValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // イベントが時刻順かチェック
        var lastTime = -1.0
        timeline.events.forEachIndexed { index, event ->
            if (event.t < 0) {
                errors.add("イベント[$index]: 時刻が負の値です")
            }
            if (event.t < lastTime) {
                warnings.add("イベント[$index]: 時刻が順序通りでありません")
            }
            lastTime = event.t
            
            // アクションに応じた検証
            when (event.action) {
                EventAction.CAPTION -> {
                    if (event.text.isNullOrBlank()) {
                        errors.add("イベント[$index]: captionにtextがありません")
                    }
                }
                EventAction.START, EventAction.STOP, EventAction.SHOT -> {
                    if (event.effect == null) {
                        errors.add("イベント[$index]: effectが指定されていません")
                    }
                    if (event.mode == null) {
                        errors.add("イベント[$index]: modeが指定されていません")
                    }
                }
            }
            
            // effect/modeの組み合わせ検証
            event.effect?.let { effect ->
                event.mode?.let { mode ->
                    if (!isValidEffectMode(effect, mode)) {
                        warnings.add("イベント[$index]: 未知のeffect/mode組み合わせ: $effect/$mode")
                    }
                }
            }
        }
        
        // start/stopのペアチェック
        val activeEffects = mutableMapOf<String, Int>()  // "effect:mode" -> startイベントindex
        timeline.events.forEachIndexed { index, event ->
            val key = "${event.effect}:${event.mode}"
            when (event.action) {
                EventAction.START -> {
                    if (activeEffects.containsKey(key)) {
                        warnings.add("イベント[$index]: $key は既にstartされています")
                    }
                    activeEffects[key] = index
                }
                EventAction.STOP -> {
                    if (!activeEffects.containsKey(key)) {
                        warnings.add("イベント[$index]: $key のstartがありません")
                    }
                    activeEffects.remove(key)
                }
                EventAction.SHOT, EventAction.CAPTION -> { /* ペア不要 */ }
            }
        }
        
        // 閉じていないstart
        activeEffects.forEach { (key, startIndex) ->
            warnings.add("イベント[$startIndex]: $key のstopがありません")
        }
        
        return TimelineValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * effect/modeの組み合わせが有効かチェック
     */
    private fun isValidEffectMode(effect: EffectType, mode: String): Boolean {
        return when (effect) {
            EffectType.VIBRATION -> VibrationMode.fromJsonMode(mode) != null
            EffectType.FLASH -> FlashMode.fromJsonMode(mode) != null
            EffectType.COLOR -> ColorMode.fromJsonMode(mode) != null
            EffectType.MIST -> MistMode.fromJsonMode(mode) != null
            EffectType.WATER -> mode == "burst"
            EffectType.WIND -> mode == "burst"
        }
    }
    
    /**
     * イベントリストをProcessedEffect（開始・終了時刻ペア）に変換
     */
    fun toProcessedEffects(timeline: Timeline): List<ProcessedEffect> {
        val result = mutableListOf<ProcessedEffect>()
        val activeStarts = mutableMapOf<String, TimelineEvent>()  // "effect:mode" -> startイベント
        
        for (event in timeline.events) {
            val key = "${event.effect}:${event.mode}"
            
            when (event.action) {
                EventAction.START -> {
                    activeStarts[key] = event
                }
                EventAction.STOP -> {
                    activeStarts[key]?.let { startEvent ->
                        result.add(
                            ProcessedEffect(
                                effect = startEvent.effect!!,
                                mode = startEvent.mode!!,
                                startTimeSec = startEvent.t,
                                endTimeSec = event.t
                            )
                        )
                    }
                    activeStarts.remove(key)
                }
                EventAction.SHOT -> {
                    // shotは瞬間的なので、開始と終了を同じにする
                    event.effect?.let { effect ->
                        event.mode?.let { mode ->
                            result.add(
                                ProcessedEffect(
                                    effect = effect,
                                    mode = mode,
                                    startTimeSec = event.t,
                                    endTimeSec = event.t  // 瞬間
                                )
                            )
                        }
                    }
                }
                EventAction.CAPTION -> { /* キャプションは別処理 */ }
            }
        }
        
        // 閉じていないstartは終了時刻なしで追加
        activeStarts.values.forEach { startEvent ->
            result.add(
                ProcessedEffect(
                    effect = startEvent.effect!!,
                    mode = startEvent.mode!!,
                    startTimeSec = startEvent.t,
                    endTimeSec = null
                )
            )
        }
        
        return result.sortedBy { it.startTimeSec }
    }
    
    /**
     * 指定時刻（秒）でアクティブなエフェクトを取得
     */
    fun getActiveEffectsAt(
        processedEffects: List<ProcessedEffect>,
        positionSec: Double
    ): List<ProcessedEffect> {
        return processedEffects.filter { effect ->
            positionSec >= effect.startTimeSec &&
            (effect.endTimeSec == null || positionSec < effect.endTimeSec)
        }
    }
    
    /**
     * 指定時刻以降の次のイベントを取得
     */
    fun getNextEvent(timeline: Timeline, positionSec: Double): TimelineEvent? {
        return timeline.events
            .filter { it.t > positionSec && it.action != EventAction.CAPTION }
            .minByOrNull { it.t }
    }
}
```

---

## 5.4 同期エンジン

### 5.4.1 domain/sync/SyncEngine.kt

```kotlin
package com.wildcard.fourd_at_home.domain.sync

import com.wildcard.fourd_at_home.domain.effect.ActionDriveController
import com.wildcard.fourd_at_home.domain.effect.EffectStationController
import com.wildcard.fourd_at_home.domain.model.EffectType
import com.wildcard.fourd_at_home.domain.model.ColorMode
import com.wildcard.fourd_at_home.domain.model.FlashMode
import com.wildcard.fourd_at_home.domain.model.MotorTarget
import com.wildcard.fourd_at_home.domain.model.ProcessedEffect
import com.wildcard.fourd_at_home.domain.model.Timeline
import com.wildcard.fourd_at_home.domain.model.VibrationMode
import com.wildcard.fourd_at_home.domain.player.VideoPlayer
import com.wildcard.fourd_at_home.domain.timeline.TimelineParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同期状態
 * 
 * JSON_SPECIFICATION.md準拠: ProcessedEffectを使用
 * - イベントベースのstart/stopペアを内部的にProcessedEffectに変換
 * - startTimeMs/endTimeMsはミリ秒単位（JSONのtは秒単位）
 */
data class SyncState(
    val isActive: Boolean = false,
    val currentPositionMs: Long = 0,
    val activeEffects: List<ProcessedEffect> = emptyList(),
    val nextEffect: ProcessedEffect? = null,
    val processedCount: Int = 0,
    val errorCount: Int = 0
)

@Singleton
class SyncEngine @Inject constructor(
    private val videoPlayer: VideoPlayer,
    private val timelineParser: TimelineParser,
    private val effectStationController: EffectStationController,
    private val actionDriveController: ActionDriveController
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var syncJob: Job? = null
    
    private var currentTimeline: Timeline? = null
    private var processedEffects: List<ProcessedEffect> = emptyList()
    
    // 実行済みエフェクトID (開始時に一度だけ実行するため)
    private val startedEffects = mutableSetOf<String>()
    // 終了処理済みエフェクトID
    private val endedEffects = mutableSetOf<String>()
    
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    /**
     * タイムラインをロード
     * 
     * JSON_SPECIFICATION.md準拠:
     * - eventsベースのタイムラインを検証
     * - start/stopペアをProcessedEffectに変換
     */
    fun loadTimeline(timeline: Timeline): Boolean {
        val validation = timelineParser.validate(timeline)
        if (!validation.isValid) {
            return false
        }
        currentTimeline = timeline
        // イベントをProcessedEffectに変換（start/stopペアリング）
        processedEffects = timelineParser.toProcessedEffects(timeline)
        return true
    }
    
    /**
     * 同期開始
     */
    fun start() {
        if (currentTimeline == null) return
        
        stop() // 既存のジョブをキャンセル
        startedEffects.clear()
        endedEffects.clear()
        
        syncJob = scope.launch {
            _syncState.value = _syncState.value.copy(isActive = true)
            
            while (isActive) {
                val positionMs = videoPlayer.getCurrentPosition()
                processEffects(positionMs)
                
                delay(16) // 約60fps
            }
        }
    }
    
    /**
     * 同期停止
     */
    fun stop() {
        syncJob?.cancel()
        syncJob = null
        _syncState.value = _syncState.value.copy(isActive = false)
        
        // 全エフェクト停止
        scope.launch {
            stopAllEffects()
        }
    }
    
    /**
     * シーク時のリセット
     */
    fun onSeek(positionMs: Long) {
        // シーク位置より前のエフェクトは実行済みとしてマーク
        // シーク位置以降のエフェクトはリセット
        processedEffects.forEach { effect ->
            if (effect.endTimeMs <= positionMs) {
                startedEffects.add(effect.id)
                endedEffects.add(effect.id)
            } else if (effect.startTimeMs > positionMs) {
                startedEffects.remove(effect.id)
                endedEffects.remove(effect.id)
            }
        }
        
        // 現在位置でアクティブなエフェクトを再開
        scope.launch {
            val activeEffects = getActiveEffectsAt(positionMs)
            activeEffects.forEach { effect ->
                if (effect.id !in startedEffects) {
                    executeEffectStart(effect)
                    startedEffects.add(effect.id)
                }
            }
        }
    }
    
    /**
     * 指定時間にアクティブなエフェクトを取得
     */
    private fun getActiveEffectsAt(positionMs: Long): List<ProcessedEffect> {
        return processedEffects.filter { effect ->
            positionMs >= effect.startTimeMs && positionMs < effect.endTimeMs
        }
    }
    
    /**
     * 次のエフェクトを取得
     */
    private fun getNextEffect(positionMs: Long): ProcessedEffect? {
        return processedEffects
            .filter { it.startTimeMs > positionMs }
            .minByOrNull { it.startTimeMs }
    }
    
    /**
     * エフェクト処理
     */
    private suspend fun processEffects(positionMs: Long) {
        val activeEffects = getActiveEffectsAt(positionMs)
        val nextEffect = getNextEffect(positionMs)
        
        // 開始処理
        activeEffects.forEach { effect ->
            if (effect.id !in startedEffects) {
                executeEffectStart(effect)
                startedEffects.add(effect.id)
                _syncState.value = _syncState.value.copy(
                    processedCount = _syncState.value.processedCount + 1
                )
            }
        }
        
        // 終了処理 (アクティブでなくなったエフェクト)
        val activeIds = activeEffects.map { it.id }.toSet()
        startedEffects.filter { id ->
            id !in activeIds && id !in endedEffects
        }.forEach { id ->
            val effect = processedEffects.find { it.id == id }
            if (effect != null) {
                executeEffectEnd(effect)
                endedEffects.add(id)
            }
        }
        
        // 状態更新
        _syncState.value = _syncState.value.copy(
            currentPositionMs = positionMs,
            activeEffects = activeEffects,
            nextEffect = nextEffect
        )
    }
    
    /**
     * エフェクト開始時の処理
     * 
     * JSON_SPECIFICATION.md準拠:
     * - effect: vibration, flash, color, water, wind, mist
     * - mode: 各エフェクト固有のモード文字列
     */
    private suspend fun executeEffectStart(effect: ProcessedEffect) {
        try {
            when (effect.effectType) {
                // 振動エフェクト
                EffectType.VIBRATION -> {
                    val vibrationMode = effect.vibrationMode ?: VibrationMode.UP_DOWN_WEAK
                    val target = vibrationMode.motorTarget
                    actionDriveController.setVibration(target, vibrationMode)
                }
                
                // フラッシュエフェクト（白色LED）
                EffectType.FLASH -> {
                    val flashMode = effect.flashMode ?: FlashMode.STEADY
                    effectStationController.setFlash(flashMode)
                }
                
                // カラーエフェクト（RGB LED）
                EffectType.COLOR -> {
                    val colorMode = effect.colorMode ?: ColorMode.RED
                    effectStationController.setColor(colorMode)
                }
                
                // 水しぶきエフェクト（ショット）
                EffectType.WATER -> {
                    effectStationController.waterShot()
                }
                
                // 風エフェクト
                EffectType.WIND -> {
                    effectStationController.windOn()
                }
                
                // ミストエフェクト
                EffectType.MIST -> {
                    effectStationController.mistShot()
                }
            }
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                errorCount = _syncState.value.errorCount + 1
            )
        }
    }
    
    /**
     * エフェクト終了時の処理
     */
    private suspend fun executeEffectEnd(effect: ProcessedEffect) {
        try {
            when (effect.effectType) {
                EffectType.VIBRATION -> {
                    val vibrationMode = effect.vibrationMode ?: VibrationMode.UP_DOWN_WEAK
                    val target = vibrationMode.motorTarget
                    actionDriveController.stop(target)
                }
                EffectType.FLASH -> {
                    effectStationController.flashOff()
                }
                EffectType.COLOR -> {
                    effectStationController.colorOff()
                }
                EffectType.WIND -> {
                    effectStationController.windOff()
                }
                EffectType.WATER, EffectType.MIST -> {
                    // WATER/MISTは瞬間的なショットなので終了処理不要
                }
            }
        } catch (e: Exception) {
            // ログ出力のみ
        }
    }
    
    /**
     * 全エフェクト停止
     */
    private suspend fun stopAllEffects() {
        effectStationController.stopAll()
        actionDriveController.stopAll()
    }
}
```

---

## 5.5 再生画面UI

### 5.5.1 ui/player/PlayerViewModel.kt

```kotlin
package com.wildcard.fourd_at_home.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildcard.fourd_at_home.domain.model.Timeline
import com.wildcard.fourd_at_home.domain.model.ProcessedEffect
import com.wildcard.fourd_at_home.domain.player.PlaybackState
import com.wildcard.fourd_at_home.domain.player.VideoPlayer
import com.wildcard.fourd_at_home.domain.sync.SyncEngine
import com.wildcard.fourd_at_home.domain.sync.SyncState
import com.wildcard.fourd_at_home.domain.timeline.TimelineParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val playbackState: PlaybackState = PlaybackState(),
    val syncState: SyncState = SyncState(),
    val timeline: Timeline? = null,
    val showControls: Boolean = true,
    val isFullscreen: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoPlayer: VideoPlayer,
    private val syncEngine: SyncEngine,
    private val timelineParser: TimelineParser
) : ViewModel() {
    
    private val _showControls = MutableStateFlow(true)
    private val _isFullscreen = MutableStateFlow(false)
    private val _timeline = MutableStateFlow<Timeline?>(null)
    private val _error = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<PlayerUiState> = combine(
        videoPlayer.playbackState,
        syncEngine.syncState,
        _timeline,
        _showControls,
        _isFullscreen,
        _error
    ) { values ->
        PlayerUiState(
            playbackState = values[0] as PlaybackState,
            syncState = values[1] as SyncState,
            timeline = values[2] as Timeline?,
            showControls = values[3] as Boolean,
            isFullscreen = values[4] as Boolean,
            error = values[5] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )
    
    /**
     * プレイヤー初期化
     */
    fun initialize() {
        videoPlayer.initialize()
    }
    
    /**
     * コンテンツをロード
     */
    fun loadContent(videoUri: String, timelineAssetPath: String) {
        viewModelScope.launch {
            try {
                // 動画をセット
                videoPlayer.setMediaItem(videoUri)
                
                // タイムラインをロード
                val result = timelineParser.parseFromAsset(timelineAssetPath)
                result.fold(
                    onSuccess = { timeline ->
                        _timeline.value = timeline
                        if (syncEngine.loadTimeline(timeline)) {
                            _error.value = null
                        } else {
                            _error.value = "タイムラインの検証に失敗しました"
                        }
                    },
                    onFailure = { e ->
                        _error.value = "タイムラインのロードに失敗: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                _error.value = "コンテンツのロードに失敗: ${e.message}"
            }
        }
    }
    
    /**
     * 再生/一時停止
     */
    fun togglePlayPause() {
        val state = videoPlayer.playbackState.value
        if (state.isPlaying) {
            videoPlayer.pause()
            syncEngine.stop()
        } else {
            videoPlayer.play()
            syncEngine.start()
        }
    }
    
    /**
     * シーク
     */
    fun seekTo(positionMs: Long) {
        videoPlayer.seekTo(positionMs)
        syncEngine.onSeek(positionMs)
    }
    
    /**
     * コントロール表示/非表示
     */
    fun toggleControls() {
        _showControls.value = !_showControls.value
    }
    
    /**
     * フルスクリーン切替
     */
    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }
    
    /**
     * プレイヤー取得
     */
    fun getPlayer() = videoPlayer.getPlayer()
    
    /**
     * クリーンアップ
     */
    override fun onCleared() {
        super.onCleared()
        syncEngine.stop()
        videoPlayer.release()
    }
}
```

### 5.5.2 ui/player/PlayerScreen.kt

```kotlin
package com.wildcard.fourd_at_home.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.wildcard.fourd_at_home.domain.model.EffectType
import com.wildcard.fourd_at_home.domain.model.ProcessedEffect
import com.wildcard.fourd_at_home.ui.theme.EffectCyan
import com.wildcard.fourd_at_home.ui.theme.EffectGreen
import com.wildcard.fourd_at_home.ui.theme.EffectOrange

@Composable
fun PlayerScreen(
    videoUri: String,
    timelineAssetPath: String,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 初期化
    LaunchedEffect(Unit) {
        viewModel.initialize()
        viewModel.loadContent(videoUri, timelineAssetPath)
    }
    
    // クリーンアップ
    DisposableEffect(Unit) {
        onDispose {
            // ViewModelのonClearedで処理
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 左側: ビデオプレーヤー
        Box(
            modifier = Modifier
                .weight(if (uiState.isFullscreen) 1f else 0.7f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.toggleControls()
                }
        ) {
            // ExoPlayer表示
            val player = viewModel.getPlayer()
            if (player != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // コントロールオーバーレイ
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PlayerControls(
                    playbackState = uiState.playbackState,
                    isFullscreen = uiState.isFullscreen,
                    onPlayPause = viewModel::togglePlayPause,
                    onSeek = viewModel::seekTo,
                    onFullscreen = viewModel::toggleFullscreen
                )
            }
        }
        
        // 右側: エフェクト情報 (非フルスクリーン時)
        if (!uiState.isFullscreen) {
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                EffectInfoPanel(
                    syncState = uiState.syncState,
                    timeline = uiState.timeline
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    playbackState: com.wildcard.fourd_at_home.domain.player.PlaybackState,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        // 中央: 再生/一時停止
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playbackState.isPlaying) "一時停止" else "再生",
                tint = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        // 下部: シークバー
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 時間表示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(playbackState.currentPositionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatTime(playbackState.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            // シークバー
            Slider(
                value = playbackState.progressPercent,
                onValueChange = { progress ->
                    val position = (playbackState.durationMs * progress).toLong()
                    onSeek(position)
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // フルスクリーンボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = "フルスクリーン",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectInfoPanel(
    syncState: com.wildcard.fourd_at_home.domain.sync.SyncState,
    timeline: com.wildcard.fourd_at_home.domain.model.Timeline?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "🎬 エフェクト情報",
            style = MaterialTheme.typography.titleMedium
        )
        
        // 同期状態
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (syncState.isActive) EffectGreen.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (syncState.isActive) "🟢 同期中" else "⏸️ 停止中",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "処理: ${syncState.processedCount}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        
        // 現在のアクティブエフェクト
        Text(
            text = "現在のエフェクト",
            style = MaterialTheme.typography.labelMedium
        )
        
        if (syncState.activeEffects.isEmpty()) {
            Text(
                text = "なし",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(syncState.activeEffects) { effect ->
                    EffectChip(effect)
                }
            }
        }
        
        // 次のエフェクト
        syncState.nextEffect?.let { next ->
            Text(
                text = "次のエフェクト",
                style = MaterialTheme.typography.labelMedium
            )
            EffectChip(
                effect = next,
                showTime = true,
                currentPositionMs = syncState.currentPositionMs
            )
        }
    }
}

@Composable
private fun EffectChip(
    effect: ProcessedEffect,
    showTime: Boolean = false,
    currentPositionMs: Long = 0
) {
    val (color, emoji) = when (effect.effectType) {
        EffectType.VIBRATION -> Color(0xFF9C27B0) to "📳"
        EffectType.FLASH -> EffectOrange to "⚡"
        EffectType.COLOR -> EffectOrange to "💡"
        EffectType.WATER -> EffectCyan to "💦"
        EffectType.WIND -> EffectGreen to "💨"
        EffectType.MIST -> EffectCyan to "🌫️"
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = effect.effectType.name,
                style = MaterialTheme.typography.labelSmall
            )
            if (showTime) {
                Spacer(modifier = Modifier.weight(1f))
                val timeUntil = effect.startTimeMs - currentPositionMs
                Text(
                    text = if (timeUntil > 0) "+${formatTime(timeUntil)}" else "NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000 / 60) % 60
    val hours = ms / 1000 / 60 / 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
```

---

## 5.6 動作テスト

### テスト項目チェックリスト

**JSON_SPECIFICATION.md準拠フォーマット対応**

| # | テスト項目 | 手順 | 期待結果 |
|:-:|:-----------|:-----|:---------|
| 1 | 動画再生 | 再生画面を開き再生ボタン | 動画が再生される |
| 2 | 一時停止 | 再生中に一時停止ボタン | 動画が止まる |
| 3 | シーク | シークバーをドラッグ | 指定位置にジャンプ |
| 4 | タイムライン読込 | events形式JSONファイルを用意 | エラーなくロード |
| 5 | vibration同期 | タイムラインのvibration start時間 | 振動開始 |
| 6 | vibration停止 | タイムラインのvibration stop時間 | 振動停止 |
| 7 | flash同期 | タイムラインのflash start時間 | 白色LED点灯・点滅 |
| 8 | color同期 | タイムラインのcolor start時間 | RGB LED色変化 |
| 9 | water shot | タイムラインのwater shot時間 | 水しぶき発射 |
| 10 | wind同期 | タイムラインのwind start時間 | 風が出る |
| 11 | mist shot | タイムラインのmist shot時間 | ミスト発射 |
| 12 | シーク時の同期 | シークして再生 | 正しい位置からエフェクト実行 |
| 13 | 停止時の全停止 | 再生停止 | 全エフェクトが停止 |
| 14 | エフェクト表示 | 再生中 | 右パネルにアクティブエフェクト表示 |
| 15 | caption表示 | タイムラインのcaption時間 | 字幕テキスト表示 |

---

## ✅ Phase 5 完了チェックリスト

- [ ] ExoPlayerで動画再生できる
- [ ] JSON_SPECIFICATION.md準拠のevents形式タイムラインをパースできる
- [ ] タイムラインバリデーションが動作する（action, effect, mode検証）
- [ ] 再生位置とエフェクトが同期する
- [ ] vibration/flash/color/water/wind/mistが正しいタイミングで実行される
- [ ] エフェクト終了時（stop action）に正しく停止する
- [ ] shot actionで瞬間的エフェクトが発動する
- [ ] シーク時に状態がリセットされる
- [ ] 再生画面UIが正しく表示される
- [ ] エフェクト情報パネルが更新される

---

## 📝 次のPhase

[Phase 6: 統合・仕上げ](./06_PHASE6_INTEGRATION.md) へ進む
