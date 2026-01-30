# Phase 6: 統合・仕上げ

**期間目安**: 3-4日  
**前提条件**: Phase 1-5 完了  
**優先度**: 高

---

## 📋 タスク一覧

| # | タスク | 優先度 | 完了条件 |
|:-:|:-------|:------:|:---------|
| 6.1 | 全体統合テスト | 必須 | 全機能結合動作 |
| 6.2 | コンテンツ管理画面 | 必須 | 内蔵コンテンツ一覧・選択 |
| 6.3 | サンプルコンテンツ作成 | 必須 | 動画+タイムライン用意 |
| 6.4 | エラーハンドリング強化 | 必須 | エラー表示・リカバリ |
| 6.5 | UI/UX改善 | 推奨 | アニメーション・フィードバック |
| 6.6 | パフォーマンス最適化 | 推奨 | メモリ・バッテリー最適化 |
| 6.7 | ドキュメント整備 | 必須 | README・使用方法 |

---

## 6.1 全体統合テスト

### 6.1.1 結合テストシナリオ

#### シナリオ1: 初回起動フロー
```
1. アプリを初回起動
2. Navigation Railが表示される
3. 設定画面に遷移
4. Bluetooth権限を許可
5. デバイススキャンを実行
6. EffectStation (4D_ES_XXXX) を接続
7. ActionDrive Motor1 (4D_AD1_XXXX) を接続
8. ActionDrive Motor2 (4D_AD2_XXXX) を接続
9. 全デバイスがREADY状態になる
```

#### シナリオ2: 手動制御フロー
```
1. 制御画面に遷移
2. 3デバイスの接続状態が表示される
3. FAN ONボタン → 風が出る
4. FAN OFFボタン → 風が止まる
5. 水しぶきボタン → 水が出る
6. LEDの色ボタン → LED点灯
7. 振動の強さボタン → 振動する
8. 全停止ボタン → 全て停止
```

#### シナリオ3: 再生同期フロー
```
1. ホーム画面でコンテンツを選択
2. 再生画面に遷移
3. 再生ボタンをタップ
4. 動画が再生開始
5. タイムラインに従ってエフェクト発動
6. エフェクト情報パネルが更新される
7. シークバーで途中にジャンプ
8. 正しい位置からエフェクト再開
9. 一時停止 → 全エフェクト停止
10. 再生終了 → 全エフェクト停止
```

#### シナリオ4: エラーリカバリフロー
```
1. 再生中にBLE接続が切れる
2. エラー通知が表示される
3. 再接続を試みる
4. 再接続成功 → 再生継続
5. 再接続失敗 → 設定画面誘導
```

### 6.1.2 テスト結果記録テンプレート

| シナリオ | ステップ | 結果 | 備考 |
|:---------|:---------|:----:|:-----|
| 1.初回起動 | 1. アプリ起動 | ⬜ | |
| | 2. Navigation Rail表示 | ⬜ | |
| | ... | | |

---

## 6.2 コンテンツ管理画面

### 6.2.1 domain/model/Content.kt

```kotlin
package com.wildcard.fourd_at_home.domain.model

/**
 * 4DXコンテンツ定義
 */
data class Content(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailResId: Int,     // 内蔵リソースID
    val videoAssetPath: String,  // assets内のパス
    val timelineAssetPath: String,
    val durationMs: Long,
    val effectTypes: List<EffectType> // 使用エフェクトの種類
)

/**
 * 内蔵コンテンツリスト
 */
object ContentLibrary {
    val contents = listOf(
        Content(
            id = "demo_001",
            title = "4DXデモ体験",
            description = "風、水、LED、振動を体験できるデモコンテンツです",
            thumbnailResId = R.drawable.thumb_demo_001,
            videoAssetPath = "videos/demo_001.mp4",
            timelineAssetPath = "timelines/demo_001.json",
            durationMs = 60000,
            effectTypes = listOf(
                EffectType.FAN,
                EffectType.SPLASH,
                EffectType.LED,
                EffectType.VIBRATION
            )
        ),
        Content(
            id = "test_fan",
            title = "FAN テスト",
            description = "風エフェクトのテスト用コンテンツ",
            thumbnailResId = R.drawable.thumb_test_fan,
            videoAssetPath = "videos/test_fan.mp4",
            timelineAssetPath = "timelines/test_fan.json",
            durationMs = 30000,
            effectTypes = listOf(EffectType.FAN)
        )
        // 他のコンテンツを追加
    )
    
    fun getById(id: String): Content? = contents.find { it.id == id }
}
```

### 6.2.2 ui/home/HomeScreen.kt

```kotlin
package com.wildcard.fourd_at_home.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wildcard.fourd_at_home.domain.model.Content
import com.wildcard.fourd_at_home.domain.model.ContentLibrary
import com.wildcard.fourd_at_home.domain.model.EffectType
import com.wildcard.fourd_at_home.ui.theme.EffectCyan
import com.wildcard.fourd_at_home.ui.theme.EffectGreen
import com.wildcard.fourd_at_home.ui.theme.EffectOrange

@Composable
fun HomeScreen(
    onContentSelected: (Content) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "4DX@HOME",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "コンテンツを選択してください",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 接続状態
            ConnectionStatusBadge(
                effectStationConnected = uiState.effectStationConnected,
                motorsConnected = uiState.motorsConnected
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // コンテンツグリッド
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ContentLibrary.contents) { content ->
                ContentCard(
                    content = content,
                    onClick = { onContentSelected(content) }
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusBadge(
    effectStationConnected: Boolean,
    motorsConnected: Int
) {
    val allConnected = effectStationConnected && motorsConnected == 2
    val color = when {
        allConnected -> EffectGreen
        effectStationConnected || motorsConnected > 0 -> EffectOrange
        else -> Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    allConnected -> "✓ 全デバイス接続済"
                    effectStationConnected -> "ES接続中 (Motor: $motorsConnected/2)"
                    motorsConnected > 0 -> "Motor: $motorsConnected/2 (ES未接続)"
                    else -> "未接続"
                },
                color = color,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ContentCard(
    content: Content,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // サムネイル
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                Image(
                    painter = painterResource(id = content.thumbnailResId),
                    contentDescription = content.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // 再生アイコンオーバーレイ
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "再生",
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                // 時間表示
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = formatDuration(content.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            // 情報
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = content.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // エフェクトタグ
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    content.effectTypes.forEach { type ->
                        EffectTag(type)
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectTag(type: EffectType) {
    val (color, emoji) = when (type) {
        EffectType.FAN -> EffectGreen to "💨"
        EffectType.SPLASH -> EffectCyan to "💦"
        EffectType.MIST -> EffectCyan to "🌫️"
        EffectType.LED -> EffectOrange to "💡"
        EffectType.VIBRATION -> Color(0xFF9C27B0) to "📳"
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = emoji,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = ms / 1000 / 60
    return String.format("%d:%02d", minutes, seconds)
}
```

### 6.2.3 ui/home/HomeViewModel.kt

```kotlin
package com.wildcard.fourd_at_home.ui.home

import androidx.lifecycle.ViewModel
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class HomeUiState(
    val effectStationConnected: Boolean = false,
    val motorsConnected: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bleDeviceManager: BleDeviceManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        // 接続状態を監視
        // 実装省略 (Phase 2のBleDeviceManager.connectionsをcollect)
    }
}
```

---

## 6.3 サンプルコンテンツ作成

### 6.3.1 ディレクトリ構造

```
app/src/main/assets/
├── videos/
│   ├── demo_001.mp4
│   ├── test_fan.mp4
│   └── ...
└── timelines/
    ├── demo_001.json
    ├── test_fan.json
    └── ...
```

### 6.3.2 サンプルタイムライン (demo_001.json)

**JSON_SPECIFICATION.md準拠フォーマット**

```json
{
  "events": [
    {"t": 0.0, "action": "caption", "text": "4DXデモ体験開始"},
    
    {"t": 0.0, "action": "start", "effect": "color", "mode": "cyan"},
    {"t": 5.0, "action": "stop", "effect": "color"},
    
    {"t": 5.0, "action": "start", "effect": "wind", "mode": "burst"},
    {"t": 10.0, "action": "stop", "effect": "wind"},
    {"t": 5.0, "action": "caption", "text": "風が吹いてきた！"},
    
    {"t": 12.0, "action": "shot", "effect": "water"},
    {"t": 12.0, "action": "caption", "text": "水しぶき！"},
    
    {"t": 15.0, "action": "start", "effect": "vibration", "mode": "up_down_mid_strong"},
    {"t": 18.0, "action": "stop", "effect": "vibration"},
    {"t": 15.0, "action": "caption", "text": "振動開始！"},
    
    {"t": 25.0, "action": "shot", "effect": "mist"},
    {"t": 27.0, "action": "shot", "effect": "mist"},
    {"t": 29.0, "action": "shot", "effect": "mist"},
    {"t": 25.0, "action": "caption", "text": "ミストエフェクト"},
    
    {"t": 30.0, "action": "start", "effect": "color", "mode": "red"},
    {"t": 35.0, "action": "stop", "effect": "color"},
    {"t": 30.0, "action": "start", "effect": "flash", "mode": "slow_blink"},
    {"t": 35.0, "action": "stop", "effect": "flash"},
    
    {"t": 40.0, "action": "start", "effect": "vibration", "mode": "heartbeat"},
    {"t": 50.0, "action": "stop", "effect": "vibration"},
    {"t": 40.0, "action": "caption", "text": "心臓の鼓動..."},
    
    {"t": 50.0, "action": "start", "effect": "wind", "mode": "burst"},
    {"t": 58.0, "action": "stop", "effect": "wind"},
    {"t": 50.0, "action": "start", "effect": "color", "mode": "purple"},
    {"t": 58.0, "action": "stop", "effect": "color"},
    {"t": 50.0, "action": "start", "effect": "flash", "mode": "fast_blink"},
    {"t": 58.0, "action": "stop", "effect": "flash"},
    {"t": 50.0, "action": "caption", "text": "フィナーレ！"},
    
    {"t": 58.0, "action": "caption", "text": "デモ終了"}
  ]
}
```

#### エフェクト・モード対応表

| 旧形式 | 新形式 (effect) | 新形式 (mode) |
|--------|-----------------|---------------|
| type: "led" (color:8) | color | cyan |
| type: "led" (color:1) | color | red |
| type: "led" (color:10) | color | purple |
| type: "led" (effect:2) | flash | fast_blink |
| type: "fan" | wind | burst |
| type: "splash" | water | burst (shotアクション) |
| type: "mist" | mist | burst (shotアクション) |
| type: "vibration" (mode:4) | vibration | up_down_mid_strong |
| type: "vibration" (mode:5) | vibration | heartbeat |

---

## 6.4 エラーハンドリング強化

### 6.4.1 共通エラー表示コンポーネント

```kotlin
package com.wildcard.fourd_at_home.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ErrorSeverity {
    WARNING, ERROR, CRITICAL
}

@Composable
fun ErrorBanner(
    message: String,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, iconColor) = when (severity) {
        ErrorSeverity.WARNING -> Color(0xFFFFF3E0) to Color(0xFFFF9800)
        ErrorSeverity.ERROR -> Color(0xFFFFEBEE) to Color(0xFFF44336)
        ErrorSeverity.CRITICAL -> Color(0xFFF44336) to Color.White
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (severity == ErrorSeverity.WARNING) 
                        Icons.Filled.Warning else Icons.Filled.Error,
                    contentDescription = null,
                    tint = iconColor
                )
                Column(
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (severity == ErrorSeverity.CRITICAL) Color.White 
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Row {
                onRetry?.let {
                    TextButton(onClick = it) {
                        Icon(Icons.Filled.Refresh, contentDescription = "再試行")
                        Text("再試行")
                    }
                }
                onDismiss?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.Filled.Close, 
                            contentDescription = "閉じる",
                            tint = iconColor
                        )
                    }
                }
            }
        }
    }
}
```

### 6.4.2 BLE切断検知と再接続

```kotlin
// BleDeviceManager に追加

/**
 * 自動再接続を試みる
 */
suspend fun attemptReconnect(deviceAddress: String): Boolean {
    val connection = connections.value[deviceAddress] ?: return false
    
    repeat(3) { attempt ->
        val result = connect(deviceAddress, connection.deviceType)
        if (result.isSuccess) {
            return true
        }
        delay(1000L * (attempt + 1)) // 指数バックオフ
    }
    return false
}

/**
 * 接続監視を開始
 */
fun startConnectionMonitoring() {
    scope.launch {
        while (isActive) {
            connections.value.forEach { (address, connection) ->
                if (connection.state == ConnectionState.DISCONNECTED) {
                    // 切断を検知
                    _connectionEvents.emit(ConnectionEvent.Disconnected(address))
                }
            }
            delay(5000) // 5秒ごとにチェック
        }
    }
}
```

---

## 6.5 UI/UX改善

### 6.5.1 ローディングインジケーター

```kotlin
@Composable
fun LoadingOverlay(
    message: String = "読み込み中...",
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = message)
                }
            }
        }
    }
}
```

### 6.5.2 エフェクト発動フィードバック

```kotlin
import com.wildcard.fourd_at_home.domain.model.ProcessedEffect
import com.wildcard.fourd_at_home.domain.model.EffectType

@Composable
fun EffectTriggerFeedback(
    activeEffects: List<ProcessedEffect>
) {
    AnimatedVisibility(
        visible = activeEffects.isNotEmpty(),
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeEffects.forEach { effect ->
                val emoji = when (effect.effectType) {
                    EffectType.VIBRATION -> "📳"
                    EffectType.FLASH -> "⚡"
                    EffectType.COLOR -> "💡"
                    EffectType.WATER -> "💦"
                    EffectType.WIND -> "💨"
                    EffectType.MIST -> "🌫️"
                }
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
```

---

## 6.6 パフォーマンス最適化

### 6.6.1 BLEコマンドバッファリング

```kotlin
/**
 * 短時間に大量のコマンドが発生した場合にバッファリング
 */
class CommandBuffer(
    private val sendCommand: suspend (String) -> Result<Unit>,
    private val minIntervalMs: Long = 50
) {
    private var lastSendTime = 0L
    private val pendingCommands = ConcurrentLinkedQueue<String>()
    
    suspend fun enqueue(command: String) {
        val now = System.currentTimeMillis()
        if (now - lastSendTime >= minIntervalMs) {
            // 即時送信
            sendCommand(command)
            lastSendTime = now
        } else {
            // バッファに追加
            pendingCommands.offer(command)
        }
    }
    
    suspend fun flush() {
        while (pendingCommands.isNotEmpty()) {
            pendingCommands.poll()?.let { command ->
                delay(minIntervalMs)
                sendCommand(command)
            }
        }
    }
}
```

### 6.6.2 メモリリーク対策

```kotlin
// ライフサイクル対応のコルーチンスコープ
class LifecycleAwareScope(
    private val lifecycle: Lifecycle
) : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        Dispatchers.Main + SupervisorJob()
    
    init {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    coroutineContext.cancel()
                }
            }
        })
    }
}
```

---

## 6.7 ドキュメント整備

### 6.7.1 README.md

```markdown
# 4DX@HOME Android

ESP32と連携して4DX体験を自宅で再現するAndroidアプリ

## 🎬 機能

- **EffectStation制御**: 風、水しぶき、ミスト、LED照明
- **ActionDrive制御**: 振動モーター (2台)
- **動画同期再生**: タイムラインに合わせたエフェクト発動
- **Bluetooth LE接続**: 最大3台のESP32と同時接続

## 📱 システム要件

- Android 8.0 (API 26) 以上
- Bluetooth LE対応デバイス
- 画面サイズ: タブレット推奨

## 🔧 ハードウェア構成

### EffectStation (ESP32)
- 風ファン (GPIO 25)
- 水しぶきポンプ (GPIO 26)
- ミスト発生器 (GPIO 32)
- RGBW LED (GPIO 27, NeoPixel)

### ActionDrive Motor1/2 (ESP32 x2)
- 振動モーター x4 (GPIO 14, 12, 13, 15)

## 🚀 セットアップ

1. ESP32ファームウェアを書き込み
2. Androidアプリをインストール
3. アプリの設定画面でデバイスを接続
4. コンテンツを選択して再生

## 📁 プロジェクト構造

```
app/src/main/java/com/wildcard/fourd_at_home/
├── ble/          # BLE通信レイヤー
├── domain/       # ビジネスロジック
│   ├── effect/   # エフェクト制御
│   ├── model/    # データモデル
│   ├── player/   # 動画再生
│   ├── sync/     # 同期エンジン
│   └── timeline/ # タイムライン処理
└── ui/           # UI (Jetpack Compose)
    ├── control/  # 手動制御画面
    ├── home/     # ホーム画面
    ├── player/   # 再生画面
    ├── settings/ # 設定画面
    └── theme/    # テーマ
```

## 📄 ライセンス

MIT License
```

---

## ✅ Phase 6 完了チェックリスト

- [ ] 全体統合テストが完了した
- [ ] 初回起動〜再生まで一連のフローが動作する
- [ ] コンテンツ一覧が表示される
- [ ] サンプルコンテンツで動作確認できた
- [ ] エラー時に適切なメッセージが表示される
- [ ] BLE切断時に通知される
- [ ] ローディング表示が適切に動作する
- [ ] README.mdが整備された
- [ ] ESP32ファームウェアのドキュメントがある

---

## 🎉 プロジェクト完了

おめでとうございます！全Phaseが完了しました。

### 次のステップ (将来拡張)
- [ ] タイムラインエディタ機能
- [ ] 外部動画ファイル対応
- [ ] 複数人同期再生
- [ ] クラウドコンテンツ配信
- [ ] エフェクト強度調整
