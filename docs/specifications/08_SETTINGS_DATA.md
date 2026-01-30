# 4D@HOME Android 詳細仕様書 - 設定・データ永続化仕様

**バージョン**: 1.0.0  
**作成日**: 2025年1月30日

---

## 📑 目次

1. [概要](#1-概要)
2. [DataStore設定](#2-datastore設定)
3. [設定キー定義](#3-設定キー定義)
4. [SettingsRepository](#4-settingsrepository)
5. [設定の適用](#5-設定の適用)
6. [コンテンツライブラリ](#6-コンテンツライブラリ)
7. [アセット管理](#7-アセット管理)

---

## 1. 概要

### 1.1 データ永続化

4D@HOME Androidアプリでは、以下のデータを永続化します。

| データ種別 | 保存方式 | 用途 |
|-----------|---------|------|
| アプリ設定 | DataStore Preferences | ユーザー設定 |
| デバイス情報 | DataStore Preferences | 接続履歴 |
| コンテンツ | Assets | 動画・タイムライン |

### 1.2 使用ライブラリ

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("androidx.datastore:datastore-preferences:1.1.2")
}
```

---

## 2. DataStore設定

### 2.1 DataStore初期化

```kotlin
// SettingsRepository.kt
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fourd_at_home_settings"
)
```

### 2.2 保存先

```
/data/data/com.wildcard.fourd_at_home/files/datastore/
└── fourd_at_home_settings.preferences_pb
```

### 2.3 データ形式

DataStore Preferencesは Protocol Buffers 形式で保存されます。

---

## 3. 設定キー定義

### 3.1 接続設定

```kotlin
companion object {
    // === 接続設定 ===
    private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    private val KEY_RECONNECT_ATTEMPTS = intPreferencesKey("reconnect_attempts")
    private val KEY_SAVED_DEVICE_ADDRESSES = stringSetPreferencesKey("saved_device_addresses")
}
```

| キー | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| `auto_reconnect` | Boolean | true | 自動再接続の有効化 |
| `reconnect_attempts` | Int | 3 | 再接続試行回数 (1-10) |
| `saved_device_addresses` | Set<String> | 空 | 保存デバイスのMACアドレス |

### 3.2 再生設定

```kotlin
    // === 再生設定 ===
    private val KEY_SYNC_OFFSET_MS = longPreferencesKey("sync_offset_ms")
    private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
    private val KEY_LAST_VIDEO_URI = stringPreferencesKey("last_video_uri")
    private val KEY_LAST_TIMELINE_URI = stringPreferencesKey("last_timeline_uri")
```

| キー | 型 | デフォルト | 範囲 | 説明 |
|------|-----|---------|------|------|
| `sync_offset_ms` | Long | 0 | -1000〜+1000 | 同期オフセット (ms) |
| `auto_play` | Boolean | false | - | 自動再生 |
| `last_video_uri` | String? | null | - | 最後に再生した動画URI |
| `last_timeline_uri` | String? | null | - | 最後に使用したタイムラインURI |

### 3.3 エフェクト設定

```kotlin
    // === エフェクト設定 ===
    private val KEY_MASTER_INTENSITY = intPreferencesKey("master_intensity")
    private val KEY_FAN_ENABLED = booleanPreferencesKey("fan_enabled")
    private val KEY_WATER_ENABLED = booleanPreferencesKey("water_enabled")
    private val KEY_MIST_ENABLED = booleanPreferencesKey("mist_enabled")
    private val KEY_LED_ENABLED = booleanPreferencesKey("led_enabled")
    private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
```

| キー | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| `master_intensity` | Int | 100 | マスター強度 (%) |
| `fan_enabled` | Boolean | true | FAN有効 |
| `water_enabled` | Boolean | true | SPLASH有効 |
| `mist_enabled` | Boolean | true | MIST有効 |
| `led_enabled` | Boolean | true | LED有効 |
| `vibration_enabled` | Boolean | true | VIBRATION有効 |

### 3.4 安全設定

```kotlin
    // === 安全設定 ===
    private val KEY_SAFETY_TIMEOUT_ENABLED = booleanPreferencesKey("safety_timeout_enabled")
    private val KEY_SAFETY_TIMEOUT_SECONDS = intPreferencesKey("safety_timeout_seconds")
```

| キー | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| `safety_timeout_enabled` | Boolean | true | 安全タイムアウト有効 |
| `safety_timeout_seconds` | Int | 30 | タイムアウト秒数 |

### 3.5 デフォルト値定数

```kotlin
    // デフォルト値
    const val DEFAULT_RECONNECT_ATTEMPTS = 3
    const val DEFAULT_SYNC_OFFSET_MS = 0L
    const val DEFAULT_MASTER_INTENSITY = 100  // パーセント
    const val DEFAULT_SAFETY_TIMEOUT_SECONDS = 30
```

---

## 4. SettingsRepository

### 4.1 クラス構造

```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 読み取り: Flow<T>
    // 書き込み: suspend fun setXxx(value: T)
}
```

### 4.2 読み取りAPI (Flow)

```kotlin
// 接続設定
val autoReconnect: Flow<Boolean>
val reconnectAttempts: Flow<Int>
val savedDeviceAddresses: Flow<Set<String>>

// 再生設定
val syncOffsetMs: Flow<Long>
val autoPlay: Flow<Boolean>
val lastVideoUri: Flow<String?>
val lastTimelineUri: Flow<String?>

// エフェクト設定
val masterIntensity: Flow<Int>
val fanEnabled: Flow<Boolean>
val waterEnabled: Flow<Boolean>
val mistEnabled: Flow<Boolean>
val ledEnabled: Flow<Boolean>
val vibrationEnabled: Flow<Boolean>

// 安全設定
val safetyTimeoutEnabled: Flow<Boolean>
val safetyTimeoutSeconds: Flow<Int>
```

### 4.3 書き込みAPI (suspend)

```kotlin
// 接続設定
suspend fun setAutoReconnect(enabled: Boolean)
suspend fun setReconnectAttempts(attempts: Int)
suspend fun addSavedDevice(address: String)
suspend fun removeSavedDevice(address: String)
suspend fun clearSavedDevices()

// 再生設定
suspend fun setSyncOffsetMs(offsetMs: Long)
suspend fun setAutoPlay(enabled: Boolean)
suspend fun setLastVideoUri(uri: String?)
suspend fun setLastTimelineUri(uri: String?)

// エフェクト設定
suspend fun setMasterIntensity(intensity: Int)
suspend fun setFanEnabled(enabled: Boolean)
suspend fun setWaterEnabled(enabled: Boolean)
suspend fun setMistEnabled(enabled: Boolean)
suspend fun setLedEnabled(enabled: Boolean)
suspend fun setVibrationEnabled(enabled: Boolean)

// 安全設定
suspend fun setSafetyTimeoutEnabled(enabled: Boolean)
suspend fun setSafetyTimeoutSeconds(seconds: Int)
```

### 4.4 実装例

```kotlin
// 読み取り
val autoReconnect: Flow<Boolean> = context.dataStore.data.map { preferences ->
    preferences[KEY_AUTO_RECONNECT] ?: true  // デフォルト: true
}

// 書き込み
suspend fun setAutoReconnect(enabled: Boolean) {
    context.dataStore.edit { preferences ->
        preferences[KEY_AUTO_RECONNECT] = enabled
    }
}

// 範囲制限付き書き込み
suspend fun setReconnectAttempts(attempts: Int) {
    context.dataStore.edit { preferences ->
        preferences[KEY_RECONNECT_ATTEMPTS] = attempts.coerceIn(1, 10)
    }
}

// Set操作 (追加)
suspend fun addSavedDevice(address: String) {
    context.dataStore.edit { preferences ->
        val current = preferences[KEY_SAVED_DEVICE_ADDRESSES] ?: emptySet()
        preferences[KEY_SAVED_DEVICE_ADDRESSES] = current + address
    }
}

// Set操作 (削除)
suspend fun removeSavedDevice(address: String) {
    context.dataStore.edit { preferences ->
        val current = preferences[KEY_SAVED_DEVICE_ADDRESSES] ?: emptySet()
        preferences[KEY_SAVED_DEVICE_ADDRESSES] = current - address
    }
}
```

---

## 5. 設定の適用

### 5.1 ViewModelでの監視

```kotlin
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playbackSyncEngine: PlaybackSyncEngine
) : ViewModel() {
    
    init {
        // 設定変更を監視して即座に適用
        viewModelScope.launch {
            settingsRepository.syncOffsetMs.collect { offset ->
                playbackSyncEngine.setSyncOffset(offset)
            }
        }
        
        viewModelScope.launch {
            combine(
                settingsRepository.fanEnabled,
                settingsRepository.waterEnabled,
                settingsRepository.mistEnabled,
                settingsRepository.ledEnabled,
                settingsRepository.vibrationEnabled
            ) { fan, water, mist, led, vibration ->
                EffectFilters(fan, water, mist, led, vibration)
            }.collect { filters ->
                playbackSyncEngine.setEffectFilters(filters)
            }
        }
    }
}
```

### 5.2 BleDeviceManagerでの適用

```kotlin
@Singleton
class BleDeviceManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    init {
        scope.launch {
            settingsRepository.autoReconnect.collect { enabled ->
                autoReconnectEnabled = enabled
            }
        }
        
        scope.launch {
            settingsRepository.reconnectAttempts.collect { attempts ->
                maxReconnectAttempts = attempts
            }
        }
    }
}
```

### 5.3 設定UIでの双方向バインディング

```kotlin
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val autoReconnect by viewModel.autoReconnect.collectAsState(initial = true)
    
    SwitchPreference(
        title = "自動再接続",
        checked = autoReconnect,
        onCheckedChange = { viewModel.setAutoReconnect(it) }
    )
}
```

---

## 6. コンテンツライブラリ

### 6.1 Content データクラス

```kotlin
/**
 * 4DXコンテンツ定義
 */
data class Content(
    val id: String,                  // 一意識別子
    val title: String,               // 表示タイトル
    val description: String,         // 説明文
    val videoAssetPath: String,      // assets内のパス (videos/xxx.mp4)
    val timelineAssetPath: String,   // assets内のパス (timelines/xxx.json)
    val durationMs: Long,            // 動画の長さ（ミリ秒）
    val effectTypes: List<EffectType> // 使用エフェクトの種類
)
```

### 6.2 EffectType 列挙型

```kotlin
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
```

### 6.3 ContentLibrary オブジェクト

```kotlin
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
            durationMs = 180000, // 約3分
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
```

### 6.4 コンテンツ追加手順

1. 動画ファイルを `app/src/main/assets/videos/` に配置
2. タイムラインJSONを `app/src/main/assets/timelines/` に配置
3. `ContentLibrary.contents` リストに新しい `Content` を追加

```kotlin
Content(
    id = "new_content_id",
    title = "新しいコンテンツ",
    description = "説明文",
    videoAssetPath = "videos/new_content.mp4",
    timelineAssetPath = "timelines/new_content.json",
    durationMs = 120000,  // 2分
    effectTypes = listOf(EffectType.FAN, EffectType.VIBRATION)
)
```

---

## 7. アセット管理

### 7.1 アセットディレクトリ構造

```
app/src/main/assets/
├── videos/
│   └── wild_speed_fire_boost.mp4
└── timelines/
    └── wild_speed_fire_boost.json
```

### 7.2 アセット読み込み

#### 動画読み込み (ExoPlayer)

```kotlin
// AssetDataSource を使用
val assetPath = "asset:///videos/wild_speed_fire_boost.mp4"
val mediaItem = MediaItem.fromUri(assetPath)
exoPlayer.setMediaItem(mediaItem)
```

#### タイムラインJSON読み込み

```kotlin
fun loadTimelineFromAssets(context: Context, assetPath: String): Timeline? {
    return try {
        context.assets.open(assetPath).use { inputStream ->
            val jsonString = inputStream.bufferedReader().readText()
            Json.decodeFromString<Timeline>(jsonString)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load timeline: $assetPath", e)
        null
    }
}
```

### 7.3 ファイルサイズ考慮

| ファイル種別 | 推奨サイズ | 備考 |
|-------------|-----------|------|
| 動画 (.mp4) | 50-200 MB | H.264/H.265 推奨 |
| タイムライン (.json) | 1-100 KB | 軽量 |

### 7.4 APKサイズへの影響

アセットファイルはAPKに含まれるため、動画ファイルのサイズがAPKサイズに直接影響します。大容量コンテンツの場合は、以下を検討してください：

- **App Bundle**: 動的配信でオンデマンドダウンロード
- **外部ストレージ**: ダウンロードしてから再生
- **ストリーミング**: ネットワーク経由で再生

---

## 付録: 設定移行

### A.1 設定のエクスポート/インポート

現在は実装されていませんが、将来的に以下の機能を追加可能です。

```kotlin
// エクスポート
suspend fun exportSettings(): String {
    val settings = mapOf(
        "auto_reconnect" to autoReconnect.first(),
        "reconnect_attempts" to reconnectAttempts.first(),
        // ...
    )
    return Json.encodeToString(settings)
}

// インポート
suspend fun importSettings(json: String) {
    val settings = Json.decodeFromString<Map<String, Any>>(json)
    // 各設定を復元
}
```

### A.2 設定のリセット

```kotlin
suspend fun resetAllSettings() {
    context.dataStore.edit { preferences ->
        preferences.clear()
    }
}
```
