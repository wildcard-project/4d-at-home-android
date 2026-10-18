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
 * 4D@HOME コンテンツ定義
 */
data class Content(
    val id: String,
    val title: String,
    val description: String,
    val videoAssetPath: String,      // assets内のパス (videos/xxx.mp4)
    val timelineAssetPath: String,   // assets内のパス (timelines/xxx.json)
    val durationMs: Long,            // 動画の長さ（ミリ秒）
    val effectTypes: List<EffectType>, // 使用エフェクトの種類
    val posterAssetPath: String? = null, // assets内のポスター画像 (posters/xxx.webp)。無ければ色の帯で代用
    val englishTitle: String = "",       // ホーム画面の大きなロゴ文字
    val year: Int? = null
)

/**
 * 内蔵コンテンツライブラリ
 *
 * タイムラインは analysis-v4 (wildcard-project/4DatHOME) で同梱動画そのものを解析して生成。
 * Fast X は同じ動画に 手動 / v4 演出多め / v4 快適 の 3 枚を並べて聴き比べられるようにしている。
 * それ以外は timelines/<id>.json = v4 演出多め、<id>.rich.json / <id>.comfort.json = モード別。
 * 動画を差し替えた場合はタイムラインも作り直すこと (別の切り出しの動画だと全体がずれる)。
 */
object ContentLibrary {
    val contents = listOf(
        Content(
            id = "wild_speed_fire_boost",
            title = "ワイルド・スピード／ファイヤーブースト（手動）",
            description = "手動で作ったタイムライン。v4 と同じ動画で聴き比べる用",
            videoAssetPath = "videos/wild_speed_fire_boost.mp4",
            timelineAssetPath = "timelines/wild_speed_fire_boost.json",
            durationMs = 151558, // 同梱動画の実測 (タイムラインはこのファイルで解析)
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.SPLASH,
                EffectType.MIST
            ),
            posterAssetPath = "posters/fast_x.webp",
            englishTitle = "FAST X",
            year = 2023
        ),
        Content(
            id = "wild_speed_fire_boost_v4_rich",
            title = "ワイルド・スピード／ファイヤーブースト（v4 演出多め）",
            description = "v4 自動解析・演出多め。音に合わせて 0.25 秒刻みで振動、衝撃で背中と座面が強く鳴る",
            videoAssetPath = "videos/wild_speed_fire_boost.mp4",
            timelineAssetPath = "timelines/wild_speed_fire_boost.rich.json",
            durationMs = 151558, // 同梱動画の実測 (タイムラインはこのファイルで解析)
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.SPLASH,
                EffectType.MIST
            ),
            posterAssetPath = "posters/fast_x.webp",
            englishTitle = "FAST X",
            year = 2023
        ),
        Content(
            id = "wild_speed_fire_boost_v4_comfort",
            title = "ワイルド・スピード／ファイヤーブースト（v4 快適）",
            description = "v4 自動解析・快適。平常時は静か、大きな爆発・衝突・水の場面だけ全力",
            videoAssetPath = "videos/wild_speed_fire_boost.mp4",
            timelineAssetPath = "timelines/wild_speed_fire_boost.comfort.json",
            durationMs = 151558, // 同梱動画の実測 (タイムラインはこのファイルで解析)
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.SPLASH,
                EffectType.MIST
            ),
            posterAssetPath = "posters/fast_x.webp",
            englishTitle = "FAST X",
            year = 2023
        ),
        Content(
            id = "sintel_trailer",
            title = "Sintel 予告編",
            description = "吹雪の山、竜との出会い、そして宿命の狩り。ファンタジーの風と衝撃を体感 (Blender Foundation, CC-BY 3.0)",
            videoAssetPath = "videos/sintel_trailer.mp4",
            timelineAssetPath = "timelines/sintel_trailer.json",
            durationMs = 52208,
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.MIST
            ),
            englishTitle = "SINTEL",
            year = 2010
        ),
        Content(
            id = "bbb_trailer",
            title = "Big Buck Bunny 予告編",
            description = "森のウサギの大反撃！コメディの間とビートに合わせた振動 (Blender Foundation, CC-BY 3.0)",
            videoAssetPath = "videos/bbb_trailer.mp4",
            timelineAssetPath = "timelines/bbb_trailer.json",
            durationMs = 33003,
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.MIST
            ),
            englishTitle = "BIG BUCK BUNNY",
            year = 2008
        ),
        Content(
            id = "fourdx_promo",
            title = "4DX@HOME プロモーション",
            description = "ロボットと怪獣、海戦、空中戦、恐竜。場面ごとに切り替わる 5 つの世界を体感",
            videoAssetPath = "videos/fourdx_promo.mp4",
            timelineAssetPath = "timelines/fourdx_promo.json",
            durationMs = 31364,
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.VIBRATION,
                EffectType.LED,
                EffectType.SPLASH,
                EffectType.MIST
            ),
            englishTitle = "4DX@HOME",
            year = 2025
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
