package com.wildcard.fourd_at_home.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * タイムラインパーサー（JSON_SPECIFICATION.md準拠）
 * JSONファイルを読み込み、タイムラインデータに変換
 */
@Singleton
class TimelineParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TimelineParser"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * URIからタイムラインを読み込む
     */
    fun parseFromUri(uri: Uri): Result<TimelineFile> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("ファイルを開けません"))
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()
            
            parseFromString(content)
        } catch (e: Exception) {
            Log.e(TAG, "タイムライン読み込み失敗", e)
            Result.failure(e)
        }
    }

    /**
     * アセットからタイムラインを読み込む
     */
    fun parseFromAssets(fileName: String): Result<TimelineFile> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()
            
            parseFromString(content)
        } catch (e: Exception) {
            Log.e(TAG, "タイムライン読み込み失敗 (assets)", e)
            Result.failure(e)
        }
    }

    /**
     * JSON文字列からタイムラインをパース（JSON_SPECIFICATION.md準拠）
     * 
     * 注意: 同じ時刻のイベントはJSONファイル内の出現順序を維持する（安定ソート）
     * これにより、同じ秒数でstop→startの順序が正しく処理される
     */
    fun parseFromString(jsonString: String): Result<TimelineFile> {
        return try {
            val timeline = json.decodeFromString<TimelineFile>(jsonString)
            
            // イベントを時間順にソート（安定ソート - 同じ時刻は元の順序を維持）
            // KotlinのsortedByは安定ソートなので、同じ時刻のイベントはJSON内の出現順序を維持
            val sortedTimeline = timeline.copy(
                events = timeline.events.sortedBy { it.t }
            )
            
            Log.d(TAG, "タイムライン読み込み完了: ${sortedTimeline.events.size}イベント")
            
            Result.success(sortedTimeline)
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析失敗", e)
            Result.failure(e)
        }
    }

    /**
     * タイムラインを検証（JSON_SPECIFICATION.md準拠）
     */
    fun validate(timeline: TimelineFile): List<String> {
        val errors = mutableListOf<String>()
        
        if (timeline.events.isEmpty()) {
            errors.add("イベントがありません")
        }
        
        var lastTime = -1.0
        timeline.events.forEachIndexed { index, event ->
            // 時刻チェック
            if (event.t < 0) {
                errors.add("イベント$index: 時刻が負の値です")
            }
            if (event.t < lastTime) {
                errors.add("イベント$index: 時刻が順序通りでありません（警告）")
            }
            lastTime = event.t
            
            // アクションに応じた検証
            when (event.action) {
                EventAction.CAPTION -> {
                    if (event.text.isNullOrBlank()) {
                        errors.add("イベント$index: captionにtextがありません")
                    }
                }
                EventAction.START, EventAction.STOP, EventAction.SHOT -> {
                    if (event.effect == null) {
                        errors.add("イベント$index: effectが指定されていません")
                    }
                    if (event.mode == null) {
                        errors.add("イベント$index: modeが指定されていません")
                    }
                    
                    // effect/modeの組み合わせ検証
                    event.effect?.let { effect ->
                        event.mode?.let { mode ->
                            if (!isValidEffectMode(effect, mode)) {
                                errors.add("イベント$index: 未知のeffect/mode組み合わせ: $effect/$mode")
                            }
                        }
                    }
                }
            }
        }
        
        return errors
    }

    /**
     * 有効なeffect/mode組み合わせかチェック
     */
    private fun isValidEffectMode(effect: EffectType, mode: String): Boolean {
        return when (effect) {
            EffectType.VIBRATION -> VibrationMode.fromJsonMode(mode) != null
            EffectType.FLASH -> FlashMode.fromJsonMode(mode) != null
            EffectType.COLOR -> ColorMode.fromJsonMode(mode) != null
            EffectType.WATER, EffectType.WIND, EffectType.MIST -> mode == "burst"
        }
    }

    /**
     * タイムラインから最大時刻を取得（ミリ秒）
     */
    fun getMaxDuration(timeline: TimelineFile): Long {
        return timeline.events.maxOfOrNull { (it.t * 1000).toLong() } ?: 0L
    }

    /**
     * サンプルタイムラインを生成（JSON_SPECIFICATION.md準拠）
     */
    fun createSampleTimeline(): TimelineFile {
        return TimelineFile(
            events = listOf(
                TimelineEventData(t = 0.0, action = EventAction.CAPTION, text = "サンプル開始"),
                TimelineEventData(t = 0.0, action = EventAction.START, effect = EffectType.COLOR, mode = "green"),
                TimelineEventData(t = 2.0, action = EventAction.START, effect = EffectType.WIND, mode = "burst"),
                TimelineEventData(t = 5.0, action = EventAction.START, effect = EffectType.VIBRATION, mode = "up_down_mid_strong"),
                TimelineEventData(t = 8.0, action = EventAction.SHOT, effect = EffectType.WATER, mode = "burst"),
                TimelineEventData(t = 10.0, action = EventAction.STOP, effect = EffectType.COLOR, mode = "green"),
                TimelineEventData(t = 10.0, action = EventAction.START, effect = EffectType.COLOR, mode = "red"),
                TimelineEventData(t = 15.0, action = EventAction.SHOT, effect = EffectType.MIST, mode = "burst"),
                TimelineEventData(t = 20.0, action = EventAction.STOP, effect = EffectType.WIND, mode = "burst"),
                TimelineEventData(t = 25.0, action = EventAction.STOP, effect = EffectType.VIBRATION, mode = "up_down_mid_strong"),
                TimelineEventData(t = 28.0, action = EventAction.STOP, effect = EffectType.COLOR, mode = "red"),
                TimelineEventData(t = 28.0, action = EventAction.CAPTION, text = "サンプル終了")
            )
        )
    }
}
