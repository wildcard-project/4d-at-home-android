package com.wildcard.fourd_at_home.domain

/**
 * エフェクトタイプ
 */
enum class EffectType {
    FAN,        // 風
    SPLASH,     // 水しぶき
    MIST,       // ミスト
    LED,        // LED照明
    VIBRATION   // 振動
}

/**
 * 4DXコンテンツ定義
 */
data class Content(
    val id: String,
    val title: String,
    val description: String,
    val videoAssetPath: String,      // assets内のパス (videos/xxx.mp4)
    val timelineAssetPath: String,   // assets内のパス (timelines/xxx.json)
    val durationMs: Long,            // 動画の長さ（ミリ秒）
    val effectTypes: List<EffectType> // 使用エフェクトの種類
)

/**
 * 内蔵コンテンツライブラリ
 */
object ContentLibrary {
    val contents = listOf(
        Content(
            id = "wild_speed_fire_boost",
            title = "ワイルドスピード ファイヤーブースト",
            description = "ローマを舞台にした迫力のカーアクション！風・振動・LEDエフェクトで臨場感を体験",
            videoAssetPath = "videos/wild_speed_fire_boost.mp4",
            timelineAssetPath = "timelines/wild_speed_fire_boost.json",
            durationMs = 180000, // 約3分（仮）
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.SPLASH
            )
        )
        // 今後コンテンツを追加する場合はここに追加
    )
    
    /**
     * IDでコンテンツを取得
     */
    fun getById(id: String): Content? = contents.find { it.id == id }
    
    /**
     * 最初のコンテンツを取得（デフォルト用）
     */
    fun getDefault(): Content? = contents.firstOrNull()
}
