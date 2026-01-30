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
 * タイムラインパーサー
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
     * JSON文字列からタイムラインをパース
     */
    fun parseFromString(jsonString: String): Result<TimelineFile> {
        return try {
            val timeline = json.decodeFromString<TimelineFile>(jsonString)
            
            // イベントを時間順にソート
            val sortedTimeline = timeline.copy(
                events = timeline.events.sortedBy { it.time }
            )
            
            Log.d(TAG, "タイムライン読み込み完了: ${sortedTimeline.title}, " +
                    "${sortedTimeline.events.size}イベント, " +
                    "${sortedTimeline.duration}ms")
            
            Result.success(sortedTimeline)
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析失敗", e)
            Result.failure(e)
        }
    }

    /**
     * タイムラインを検証
     */
    fun validate(timeline: TimelineFile): List<String> {
        val errors = mutableListOf<String>()
        
        if (timeline.events.isEmpty()) {
            errors.add("イベントがありません")
        }
        
        timeline.events.forEachIndexed { index, event ->
            if (event.time < 0) {
                errors.add("イベント$index: 時間が負の値です")
            }
            
            when (event.type) {
                EventType.FAN, EventType.WATER, EventType.MIST, EventType.VIBRATION -> {
                    if (event.params.intensity !in 0..255) {
                        errors.add("イベント$index: intensity は 0-255 の範囲で指定してください")
                    }
                }
                EventType.LED -> {
                    if (event.params.r !in 0..255 ||
                        event.params.g !in 0..255 ||
                        event.params.b !in 0..255) {
                        errors.add("イベント$index: RGB値は 0-255 の範囲で指定してください")
                    }
                }
            }
        }
        
        return errors
    }

    /**
     * サンプルタイムラインを生成
     */
    fun createSampleTimeline(): TimelineFile {
        return TimelineFile(
            version = "1.0",
            title = "サンプルタイムライン",
            duration = 30000,  // 30秒
            events = listOf(
                // 0秒: LED緑点灯
                TimelineEventData(
                    time = 0,
                    type = EventType.LED,
                    params = EventParams(r = 0, g = 255, b = 0, brightness = 200)
                ),
                // 2秒: ファン開始
                TimelineEventData(
                    time = 2000,
                    type = EventType.FAN,
                    params = EventParams(intensity = 128)
                ),
                // 5秒: 振動開始
                TimelineEventData(
                    time = 5000,
                    type = EventType.VIBRATION,
                    params = EventParams(intensity = 180, motor = 0)
                ),
                // 8秒: 水噴射
                TimelineEventData(
                    time = 8000,
                    type = EventType.WATER,
                    params = EventParams(intensity = 200, duration = 500)
                ),
                // 10秒: LED赤に変更
                TimelineEventData(
                    time = 10000,
                    type = EventType.LED,
                    params = EventParams(r = 255, g = 0, b = 0, brightness = 255)
                ),
                // 15秒: ミスト開始
                TimelineEventData(
                    time = 15000,
                    type = EventType.MIST,
                    params = EventParams(intensity = 150)
                ),
                // 20秒: ファン最大
                TimelineEventData(
                    time = 20000,
                    type = EventType.FAN,
                    params = EventParams(intensity = 255)
                ),
                // 25秒: 振動停止
                TimelineEventData(
                    time = 25000,
                    type = EventType.VIBRATION,
                    params = EventParams(intensity = 0)
                ),
                // 28秒: 全停止
                TimelineEventData(
                    time = 28000,
                    type = EventType.ALL_OFF,
                    params = EventParams()
                )
            )
        )
    }
}
