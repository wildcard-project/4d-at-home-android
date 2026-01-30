# 4D@HOME Android 詳細仕様書 - UIコンポーネント仕様

**バージョン**: 1.0.0  
**作成日**: 2025年1月30日

---

## 📑 目次

1. [概要](#1-概要)
2. [画面構成](#2-画面構成)
3. [ナビゲーション](#3-ナビゲーション)
4. [再生画面 (PlaybackScreen)](#4-再生画面-playbackscreen)
5. [制御画面 (ControlScreen)](#5-制御画面-controlscreen)
6. [設定画面 (SettingsScreen)](#6-設定画面-settingsscreen)
7. [テーマとスタイル](#7-テーマとスタイル)
8. [ViewModel構成](#8-viewmodel構成)

---

## 1. 概要

### 1.1 UIフレームワーク

| 項目 | 技術 |
|------|------|
| **UIフレームワーク** | Jetpack Compose |
| **デザインシステム** | Material 3 |
| **BOM バージョン** | 2024.12.01 |
| **ナビゲーション** | Navigation Compose 2.8.5 |
| **DI** | Hilt Navigation Compose |

### 1.2 画面一覧

| 画面 | ルート | 説明 |
|------|--------|------|
| 再生画面 | `/playback` | コンテンツ選択・再生 |
| 制御画面 | `/control` | 手動エフェクト制御 |
| 設定画面 | `/settings` | アプリ設定 |

---

## 2. 画面構成

### 2.1 ランドスケープレイアウト

本アプリはタブレット使用を想定し、**横画面（ランドスケープ）**を基本としています。

```
┌─────────────────────────────────────────────────────────┐
│  ┌────────┐                                             │
│  │  Icon  │                                             │
│  │ Play   │                                             │
│  ├────────┤              Content Area                   │
│  │  Icon  │                                             │
│  │Control │        (Selected Screen Content)            │
│  ├────────┤                                             │
│  │  Icon  │                                             │
│  │Settings│                                             │
│  └────────┘                                             │
│  Navigation                                             │
│     Rail                                                │
└─────────────────────────────────────────────────────────┘
```

### 2.2 レイアウト構成

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    Row(modifier = Modifier.fillMaxSize()) {
        // ナビゲーションレール（左側）
        NavigationRail(
            modifier = Modifier.fillMaxHeight()
        ) {
            NavigationRailItem(
                icon = { Icon(Icons.Default.PlayArrow, "再生") },
                selected = currentRoute == "playback",
                onClick = { navController.navigate("playback") }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Tune, "制御") },
                selected = currentRoute == "control",
                onClick = { navController.navigate("control") }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, "設定") },
                selected = currentRoute == "settings",
                onClick = { navController.navigate("settings") }
            )
        }
        
        // コンテンツエリア（右側）
        NavHost(
            navController = navController,
            startDestination = "playback"
        ) {
            composable("playback") { PlaybackScreen() }
            composable("control") { ControlScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
```

---

## 3. ナビゲーション

### 3.1 ルート定義

```kotlin
// 推奨: sealed classでルート管理
sealed class Screen(val route: String) {
    object Playback : Screen("playback")
    object Control : Screen("control")
    object Settings : Screen("settings")
}
```

### 3.2 ナビゲーション遷移

| 遷移元 | 遷移先 | トリガー |
|--------|--------|---------|
| 任意 | Playback | NavigationRail タップ |
| 任意 | Control | NavigationRail タップ |
| 任意 | Settings | NavigationRail タップ |
| Playback (再生中) | Playback (選択) | 戻るボタン |

### 3.3 状態保持

```kotlin
// 各画面はViewModelで状態を保持
@HiltViewModel
class PlaybackViewModel : ViewModel() {
    // 画面遷移してもViewModelは生存
    // (HiltViewModelは NavBackStackEntry にスコープされる)
}
```

---

## 4. 再生画面 (PlaybackScreen)

### 4.1 画面状態

```kotlin
data class PlaybackUiState(
    // コンテンツ選択
    val showContentSelector: Boolean = true,
    val availableContents: List<Content> = emptyList(),
    val selectedContent: Content? = null,
    
    // 再生状態
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackProgress: Float = 0f,
    
    // 接続状態
    val isEffectStationConnected: Boolean = false,
    val isMotor1Connected: Boolean = false,
    val isMotor2Connected: Boolean = false,
    
    // エフェクト表示
    val activeEffects: Set<EffectType> = emptySet(),
    val nextEvent: TimelineEvent? = null,
    
    // エラー
    val error: String? = null
)
```

### 4.2 コンテンツ選択画面

```
┌──────────────────────────────────────────────────────────────┐
│  4DX@HOME                          [接続状態バッジ]           │
│  コンテンツを選択してください                                   │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────┐ │
│  │  [サムネイル]                                           │ │
│  │                                                        │ │
│  │  ワイルドスピード ファイヤーブースト                      │ │
│  │  ローマを舞台にした迫力のカーアクション！                  │ │
│  │                                                        │ │
│  │  エフェクト: 🌬️ 💧 💡 📳                               │ │
│  │  再生時間: 3:00                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  [その他のコンテンツカード...]                                │
└──────────────────────────────────────────────────────────────┘
```

### 4.3 再生コンテンツ画面

```
┌──────────────────────────────────────────────────────────────┐
│  [←戻る]  ワイルドスピード                [緊急停止ボタン]    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                                                      │   │
│  │                   動画プレーヤー                      │   │
│  │                   (PlayerView)                       │   │
│  │                                                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━  1:30 / 3:00              │
│                                                              │
│  [⏮️]  [▶️/⏸️]  [⏭️]                                        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│  アクティブエフェクト: 🌬️ FAN ON  │  📳 VIBRATION STRONG     │
│                                                              │
│  次のイベント: 1:32 - SPLASH                                  │
└──────────────────────────────────────────────────────────────┘
```

### 4.4 ViewModel操作

```kotlin
class PlaybackViewModel {
    // コンテンツ操作
    fun loadContent(content: Content)
    fun showContentSelector()
    
    // 再生制御
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekForward()
    fun seekBack()
    
    // エラー処理
    fun clearError()
    
    // 緊急停止
    fun emergencyStop()
}
```

---

## 5. 制御画面 (ControlScreen)

### 5.1 画面状態

```kotlin
data class ControlUiState(
    val effectState: EffectState = EffectState(),
    val isEffectStationConnected: Boolean = false,
    val isMotor1Connected: Boolean = false,
    val isMotor2Connected: Boolean = false,
    val lastError: String? = null,
    val isSending: Boolean = false
)

data class EffectState(
    // EffectStation
    val fanOn: Boolean = false,
    val mistMode: MistMode = MistMode.OFF,
    val ledColor: LedColorPreset = LedColorPreset.OFF,
    val ledBrightness: LedBrightnessLevel = LedBrightnessLevel.OFF,
    val ledEffect: LedEffectMode = LedEffectMode.STEADY,
    val ledTransition: LedTransitionMode = LedTransitionMode.INSTANT,
    // ActionDrive
    val motor1Level: VibrationLevel = VibrationLevel.OFF,
    val motor2Level: VibrationLevel = VibrationLevel.OFF
)
```

### 5.2 画面レイアウト

```
┌──────────────────────────────────────────────────────────────┐
│  エフェクト制御                              [全停止ボタン]   │
├──────────────────────────────────────────────────────────────┤
│  [EffectStation ●] [Motor1 ●] [Motor2 ○]  ← 接続状態        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─ EffectStation ───────────────────────────────────────┐ │
│  │                                                        │ │
│  │  🌬️ FAN    [ON] [OFF]                                 │ │
│  │                                                        │ │
│  │  💧 SPLASH [発射！]                                    │ │
│  │                                                        │ │
│  │  🌫️ MIST   [OFF] [一瞬] [継続]                        │ │
│  │                                                        │ │
│  │  💡 LED                                                │ │
│  │    色:   [🔴][🟠][🟡][🟢][🔵][🟣][⚪][⚫]              │ │
│  │    明るさ: [OFF] [弱] [強]                             │ │
│  │    効果:   [点灯] [点滅] [呼吸]                        │ │
│  │    切替:   [一瞬] [フェード]                           │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─ ActionDrive ─────────────────────────────────────────┐ │
│  │                                                        │ │
│  │  Motor1                Motor2                          │ │
│  │  [OFF][弱][中弱][中強][強]  [OFF][弱][中弱][中強][強]   │ │
│  │                                                        │ │
│  │  両方同時: [OFF][弱][中弱][中強][強]                    │ │
│  │                                                        │ │
│  │  パターン: [心拍❤️] [高速振動⚡] [低速振動🌊]            │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 LED色プリセット

```kotlin
enum class LedColorPreset(val displayName: String, val colorId: Int) {
    PINK("ピンク", 0),
    RED("赤", 1),
    ORANGE("オレンジ", 2),
    YELLOW("黄色", 3),
    YELLOW_GREEN("黄緑", 4),
    GREEN("緑", 5),
    DARK_GREEN("深緑", 6),
    CYAN("水色", 7),
    BLUE("青", 8),
    PURPLE("紫", 9),
    WHITE("白", 10),
    OFF("消灯", 11)
}
```

### 5.4 ViewModel操作

```kotlin
class ControlViewModel {
    // ファン
    fun toggleFan()
    fun setFan(on: Boolean)
    
    // 水噴射
    fun triggerSplash()
    
    // ミスト
    fun setMistMode(mode: MistMode)
    
    // LED
    fun setLedColor(color: LedColorPreset)
    fun setLedBrightness(brightness: LedBrightnessLevel)
    fun setLedEffect(effect: LedEffectMode)
    fun setLedTransition(transition: LedTransitionMode)
    fun ledOff()
    
    // 振動
    fun setMotor1Level(level: VibrationLevel)
    fun setMotor2Level(level: VibrationLevel)
    fun setBothMotorsLevel(level: VibrationLevel)
    fun sendMotor1Pattern(pattern: VibrationPattern)
    fun sendMotor2Pattern(pattern: VibrationPattern)
    fun sendBothMotorsPattern(pattern: VibrationPattern)
    
    // 停止
    fun stopAllEffects()
    fun stopEffectStation()
    fun stopMotors()
    
    // エラー
    fun clearError()
}
```

---

## 6. 設定画面 (SettingsScreen)

### 6.1 設定カテゴリ

| カテゴリ | 説明 |
|---------|------|
| 接続設定 | BLE接続に関する設定 |
| 再生設定 | 動画再生と同期に関する設定 |
| エフェクト設定 | エフェクト有効/無効 |
| 安全設定 | タイムアウトなど |

### 6.2 設定項目

#### 接続設定

| 項目 | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| 自動再接続 | Boolean | true | 切断時の自動再接続 |
| 再接続試行回数 | Int | 3 | 再接続の最大試行回数 |
| 保存デバイス | Set<String> | 空 | 保存されたデバイスアドレス |

#### 再生設定

| 項目 | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| 同期オフセット | Long | 0 | エフェクト発火タイミング調整 (ms) |
| 自動再生 | Boolean | false | コンテンツ読み込み後に自動再生 |
| 最後の動画URI | String? | null | 前回再生した動画 |
| 最後のタイムラインURI | String? | null | 前回使用したタイムライン |

#### エフェクト設定

| 項目 | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| マスター強度 | Int | 100 | 全体の強度 (%) |
| FAN有効 | Boolean | true | 風エフェクトの有効化 |
| SPLASH有効 | Boolean | true | 水エフェクトの有効化 |
| MIST有効 | Boolean | true | ミストエフェクトの有効化 |
| LED有効 | Boolean | true | LEDエフェクトの有効化 |
| VIBRATION有効 | Boolean | true | 振動エフェクトの有効化 |

#### 安全設定

| 項目 | 型 | デフォルト | 説明 |
|------|-----|---------|------|
| 安全タイムアウト有効 | Boolean | true | タイムアウトの有効化 |
| 安全タイムアウト秒数 | Int | 30 | 自動停止までの秒数 |

### 6.3 画面レイアウト

```
┌──────────────────────────────────────────────────────────────┐
│  設定                                                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ▼ 接続設定                                                  │
│  ├── 自動再接続              [スイッチ ON]                   │
│  └── 再接続試行回数          [3回 ▼]                         │
│                                                              │
│  ▼ 再生設定                                                  │
│  ├── 同期オフセット          [-1000 ───○─── +1000] 0ms      │
│  └── 自動再生               [スイッチ OFF]                   │
│                                                              │
│  ▼ エフェクト設定                                            │
│  ├── マスター強度           [0 ────────●] 100%              │
│  ├── 🌬️ FAN               [スイッチ ON]                     │
│  ├── 💧 SPLASH             [スイッチ ON]                     │
│  ├── 🌫️ MIST              [スイッチ ON]                     │
│  ├── 💡 LED                [スイッチ ON]                     │
│  └── 📳 VIBRATION          [スイッチ ON]                     │
│                                                              │
│  ▼ 安全設定                                                  │
│  ├── 安全タイムアウト        [スイッチ ON]                   │
│  └── タイムアウト秒数        [30秒 ▼]                        │
│                                                              │
│  ▼ デバイス管理                                              │
│  └── 保存済みデバイスをクリア  [クリア]                       │
│                                                              │
│  ▼ アプリ情報                                                │
│  ├── バージョン             1.0.0                           │
│  └── ビルド                 2025.01.30                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 7. テーマとスタイル

### 7.1 カラーパレット

```kotlin
// Primary Colors
val NeonCyan = Color(0xFF00FFFF)
val NeonMagenta = Color(0xFFFF00FF)
val NeonRed = Color(0xFFFF0040)

// Background
val DeepBlack = Color(0xFF0A0A0F)
val DarkGray = Color(0xFF1A1A2E)

// Surface
val CardSurface = Color(0xFF16213E)
val CardSurfaceVariant = Color(0xFF1F2940)

// Status
val StatusConnected = Color(0xFF00FF88)
val StatusDisconnected = Color(0xFFFF4444)
val StatusWarning = Color(0xFFFFAA00)

// Text
val TextPrimary = Color(0xFFE8E8E8)
val TextSecondary = Color(0xFFB0B0B0)
```

### 7.2 テーマ定義

```kotlin
@Composable
fun FourdAtHomeTheme(
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme(
        primary = NeonCyan,
        secondary = NeonMagenta,
        tertiary = NeonRed,
        background = DeepBlack,
        surface = CardSurface,
        surfaceVariant = CardSurfaceVariant,
        onPrimary = DeepBlack,
        onSecondary = DeepBlack,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography,
        content = content
    )
}
```

### 7.3 デザインコンセプト

| 要素 | 説明 |
|------|------|
| **テーマ** | 「ポケットシアター」- 映画館の暗闘をイメージ |
| **配色** | ダークテーマ + ネオンアクセント |
| **形状** | 角丸カード (RoundedCornerShape) |
| **アイコン** | Material Icons Filled |

---

## 8. ViewModel構成

### 8.1 ViewModel一覧

| ViewModel | 画面 | 責務 |
|-----------|------|------|
| `PlaybackViewModel` | PlaybackScreen | 再生制御、タイムライン同期 |
| `ControlViewModel` | ControlScreen | 手動エフェクト制御 |
| `SettingsViewModel` | SettingsScreen | 設定管理 |

### 8.2 依存関係

```
┌─────────────────────┐
│   ViewModels        │
├─────────────────────┤
│ PlaybackViewModel   │───┬──> PlaybackSyncEngine
│                     │   ├──> TimelineParser  
│                     │   ├──> BleDeviceManager
│                     │   └──> SettingsRepository
├─────────────────────┤
│ ControlViewModel    │───┬──> CommandSender
│                     │   └──> BleDeviceManager
├─────────────────────┤
│ SettingsViewModel   │───┬──> SettingsRepository
│                     │   └──> BleDeviceManager
└─────────────────────┘
```

### 8.3 状態管理

```kotlin
// StateFlow パターン
@HiltViewModel
class ExampleViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Compose で収集
    // val state by viewModel.uiState.collectAsState()
}
```
