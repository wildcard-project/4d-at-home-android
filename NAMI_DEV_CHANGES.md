# nami-dev ブランチ変更履歴

## 2026年2月1日 - UI画面遷移の改修

### 概要
画面構成を「動画選択 → セッティング(BLE) → 動画再生」の直線フローに変更。既存のボタン項目とロジックは維持し、UIとルーティングのみ最小限で改修。

### 変更方針
- ✅ **既存UIのボタン項目を変更しない** - ラベル、役割、配置を維持
- ✅ **ロジックに触れない** - BLE送信、接続、タイムライン処理、状態管理は未変更
- ✅ **最小限の変更** - 画面構造、ルーティング、レイアウト調整のみ
- ✅ **既存構造の再利用** - ViewModelやRepositoryはそのまま使用

---

## 新規作成ファイル

### 1. `app/src/main/java/com/wildcard/fourd_at_home/ui/videoselect/VideoSelectScreen.kt`
**目的:** 動画選択画面（カード型サムネイルデザイン）

**主な機能:**
- 暗い背景（Black）に縦長サムネカード表示
- ContentLibraryから動画一覧を取得
- カードタップでvideoIdを渡してSettingsScreenへ遷移
- エフェクトタグ（風/水/霧/光/衝）の表示

**使用コンポーネント:**
- `VideoSelectScreen(onVideoSelected: (Content) -> Unit)` - メイン画面
- `VideoCard()` - 動画カード
- `EffectTag()` - エフェクトタグ

---

## 変更ファイル

### 2. `app/src/main/java/com/wildcard/fourd_at_home/ui/navigation/AppNavigation.kt`
**変更内容:** NavigationRailを削除し、直線フローに変更

**Before:**
- NavigationRail使用（Playback/Control/Settingsの3タブ）
- タブ切替による画面遷移

**After:**
- 直線フロー: `VideoSelect → Settings/{videoId} → Playback/{videoId}`
- navigation argumentでvideoIdを受け渡し
- システムの戻るボタンで前画面に戻れる

**ルート定義:**
```kotlin
sealed class Screen(val route: String) {
    data object VideoSelect : Screen("video_select")
    data object Settings : Screen("settings/{videoId}")
    data object Playback : Screen("playback/{videoId}")
}
```

---

### 3. `app/src/main/java/com/wildcard/fourd_at_home/ui/settings/SettingsScreen.kt`
**変更内容:** 「再生へ」ボタンを追加（既存UIは維持）

**追加パラメータ:**
- `videoId: String = ""` - 選択された動画ID
- `onNavigateToPlayback: () -> Unit = {}` - 再生画面への遷移コールバック

**追加UI:**
```kotlin
Button(
    onClick = onNavigateToPlayback,
    enabled = hasConnections,  // デバイス接続時のみ有効
    modifier = Modifier.fillMaxWidth()
) {
    Text(if (hasConnections) "再生へ" else "デバイスを接続してください")
}
```

**既存機能（変更なし）:**
- スキャン開始/停止ボタン
- 接続/切断ボタン
- デバイス一覧表示
- コマンドログ表示

---

### 4. `app/src/main/java/com/wildcard/fourd_at_home/ui/playback/PlaybackScreen.kt`
**変更内容:** ContentSelectorScreenを削除、videoIdでコンテンツを自動ロード

**削除したコンポーネント:**
- `ContentSelectorScreen()` - VideoSelectScreenに移動
- `ConnectionStatusBadge()` - 不要
- `ContentCard()` - VideoSelectScreenで再実装
- `EffectTag()` - VideoSelectScreenで再実装

**変更した関数シグネチャ:**
```kotlin
// Before
fun PlaybackScreen(viewModel: PlaybackViewModel = hiltViewModel())

// After
fun PlaybackScreen(
    videoId: String,
    viewModel: PlaybackViewModel = hiltViewModel()
)
```

**追加ロジック:**
```kotlin
// videoIdに基づいてコンテンツを自動ロード
if (uiState.selectedContent == null && videoId.isNotEmpty()) {
    viewModel.loadContentById(videoId)
}
```

**削除した機能:**
- `onBack` パラメータ（戻るボタン） - システムの戻るで代替
- コンテンツ選択画面の表示切替

**整理したインポート:**
- LazyColumn, items（削除）
- ArrowBack, Air, Lightbulb, Stop, Vibration, WaterDrop（削除）
- Surface, clip, background, clickable（削除）

---

### 5. `app/src/main/java/com/wildcard/fourd_at_home/ui/playback/PlaybackViewModel.kt`
**変更内容:** videoIdでコンテンツをロードする機能を追加

**追加メソッド:**
```kotlin
/**
 * IDでコンテンツを読み込む
 */
fun loadContentById(videoId: String) {
    val content = ContentLibrary.contents.find { it.id == videoId }
    if (content != null) {
        loadContent(content)
    } else {
        _uiState.value = _uiState.value.copy(
            error = "コンテンツが見つかりません"
        )
    }
}
```

**削除したメソッド:**
- `showContentSelector()` - 不要
- `hideContentSelector()` - 不要

**削除したUiStateフィールド:**
- `showContentSelector: Boolean` - 不要

**既存機能（変更なし）:**
- `loadContent()` - 内部実装は未変更
- ExoPlayer初期化/管理
- PlaybackSyncEngine連携
- BLE接続状態の監視

---

## 画面遷移フロー

### Before（タブ切替）
```
┌─────────────────────────────────┐
│  NavigationRail (常に表示)      │
│  ├─ 再生                        │
│  ├─ 制御                        │
│  └─ 設定                        │
└─────────────────────────────────┘
```

### After（直線フロー）
```
VideoSelectScreen
    │ (動画カードタップ)
    ↓ videoId を渡す
SettingsScreen
    │ BLE接続
    │ (「再生へ」ボタン)
    ↓ videoId を保持
PlaybackScreen
    │ (システム戻る)
    ↓
SettingsScreen
    │ (システム戻る)
    ↓
VideoSelectScreen
```

---

## 変更していない部分（重要）

### ロジック層（未変更）
- `PlaybackSyncEngine` - タイムライン同期エンジン
- `TimelineParser` - JSONパーサー
- `BleDeviceManager` - BLE接続管理
- `BleConnection` - BLE通信
- `CommandSender` - コマンド送信
- すべてのViewModel内部ロジック

### データ層（未変更）
- `Content` データクラス
- `ContentLibrary` オブジェクト
- `TimelineFile` モデル
- `SettingsRepository`

### 既存UI（未変更）
- ControlScreen（※今回の画面遷移からは外れているが、コード自体は残存）
- SettingsScreenの既存ボタン（スキャン/接続/切断）
- PlaybackScreenの再生コントロール
- エフェクト表示ロジック

---

## 動作確認項目

- [ ] VideoSelectScreenで動画一覧が表示される
- [ ] カードタップでSettingsScreenに遷移し、videoIdが渡される
- [ ] SettingsScreenで既存のBLE機能が正常動作する
- [ ] デバイス未接続時「再生へ」ボタンが無効化される
- [ ] デバイス接続時「再生へ」ボタンが有効化される
- [ ] 「再生へ」タップでPlaybackScreenに遷移し、動画がロードされる
- [ ] システム戻るボタンで前画面に戻れる
- [ ] PlaybackScreenでタイムライン同期が正常動作する
- [ ] BLEコマンド送信が正常動作する

---

## ビルドに関する注意

Gradleビルドで環境依存のエラーが発生する場合：

```bash
# Android Studioでプロジェクトを開く
# File → Invalidate Caches / Restart を実行

# または手動クリーン
rm -rf .gradle build app/build
./gradlew clean assembleDebug
```

Android SDKとbuild toolsが正しくインストールされているか確認してください。

---

## 次のステップ（オプション）

### 今後の改善案
1. **VideoSelectScreenのUI強化**
   - 横スワイプカルーセル実装
   - 中央カード強調表示
   - サムネイル画像の実装

2. **PlaybackScreenのUI改善**
   - 左縦並びカテゴリ丸ボタン（衝/光/風/水/色）
   - ActiveEffects表示の強化
   - 上部シーン説明テキスト領域

3. **ControlScreenの統合**
   - 必要に応じてSettingsScreenまたはPlaybackScreenに統合
   - または完全に削除

4. **エラーハンドリング強化**
   - videoIdが不正な場合のフォールバック
   - BLE接続失敗時のリトライ機能

---

## コミット情報

**ブランチ:** nami-dev  
**日付:** 2026年2月1日  
**作業者:** AI Agent  
**変更ファイル数:** 5ファイル（新規1、変更4）
