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
            videoAssetPath = "wild_speed_fire_boost.mp4",
            timelineAssetPath = "timelines/wild_speed_fire_boost.json",
            durationMs = 180000, // 約3分（仮）
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.SPLASH
            )
        ),
        Content(
            id = "space_adventure",
            title = "スペースアドベンチャー",
            description = "宇宙空間での壮大な冒険！ミスト・光・振動で宇宙の神秘を体感",
            videoAssetPath = "videos/space_adventure.mp4",
            timelineAssetPath = "timelines/space_adventure.json",
            durationMs = 150000,
            effectTypes = listOf(
                EffectType.MIST,
                EffectType.LED,
                EffectType.VIBRATION
            )
        ),
        Content(
            id = "ocean_depths",
            title = "オーシャンデプス 深海の謎",
            description = "深海の神秘的な世界へダイブ！水しぶき・ミスト・青のLEDで海中を再現",
            videoAssetPath = "videos/ocean_depths.mp4",
            timelineAssetPath = "timelines/ocean_depths.json",
            durationMs = 200000,
            effectTypes = listOf(
                EffectType.SPLASH,
                EffectType.MIST,
                EffectType.LED
            )
        ),
        Content(
            id = "mountain_storm",
            title = "マウンテンストーム",
            description = "嵐の山岳地帯を駆け抜ける！強風・水・振動で自然の猛威を体験",
            videoAssetPath = "videos/mountain_storm.mp4",
            timelineAssetPath = "timelines/mountain_storm.json",
            durationMs = 170000,
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.SPLASH,
                EffectType.VIBRATION,
                EffectType.LED
            )
        ),
        Content(
            id = "city_chase",
            title = "シティチェイス 都市追跡",
            description = "都会の夜を疾走するカーチェイス！振動・LED・風で迫力満点",
            videoAssetPath = "videos/city_chase.mp4",
            timelineAssetPath = "timelines/city_chase.json",
            durationMs = 160000,
            effectTypes = listOf(
                EffectType.VIBRATION,
                EffectType.FAN,
                EffectType.LED
            )
        )
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
