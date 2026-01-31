# 4D@HOME Android 詳細仕様書 - システム連携仕様

**バージョン**: 1.0.0  
**作成日**: 2025年1月30日

---

## 📑 目次

1. [概要](#1-概要)
2. [システム構成](#2-システム構成)
3. [接続フロー](#3-接続フロー)
4. [再生同期フロー](#4-再生同期フロー)
5. [コマンド送信シーケンス](#5-コマンド送信シーケンス)
6. [エラーハンドリング](#6-エラーハンドリング)
7. [設定データフロー](#7-設定データフロー)
8. [アプリケーションライフサイクル](#8-アプリケーションライフサイクル)
9. [データフォーマット相互参照](#9-データフォーマット相互参照)

---

## 1. 概要

### 1.1 システム連携とは

4D@HOME Androidシステムは、Androidアプリケーション、複数のESP32デバイス、およびメディアファイルが連携して動作します。本書では、これらのコンポーネント間のデータフローと同期メカニズムを解説します。

### 1.2 連携コンポーネント

| コンポーネント | 役割 |
|---------------|------|
| **Android App** | 中央制御、UI提供、BLEマスター |
| **EffectStation** | 環境エフェクト（風・水・ミスト・LED）制御 |
| **ActionDrive 1/2** | 振動エフェクト制御 |
| **メディアファイル** | 動画(.mp4) + タイムライン(.json) |

---

## 2. システム構成

### 2.1 全体アーキテクチャ

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Device                            │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                     UI Layer                            │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │ │
│  │  │HomeScreen│ │PlayScreen│ │DevicesScr│ │SettingsScr│ │ │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬──────┘ │ │
│  └───────┴────────────┴────────────┴────────────┴─────────┘ │
│                          │                                   │
│  ┌───────────────────────▼────────────────────────────────┐ │
│  │                ViewModel Layer                          │ │
│  │    PlaybackViewModel ←→ DevicesViewModel               │ │
│  └───────────────────────┬────────────────────────────────┘ │
│                          │                                   │
│  ┌───────────────────────▼────────────────────────────────┐ │
│  │              Business Logic Layer                       │ │
│  │  ┌────────────────┐    ┌────────────────────────────┐  │ │
│  │  │PlaybackSyncEngine│   │BleDeviceManager            │  │ │
│  │  │                 │   │  └── CommandSender         │  │ │
│  │  │ ├─ ExoPlayer   │   │  └── BleScanner            │  │ │
│  │  │ └─ TimelineParser│  │                            │  │ │
│  │  └────────────────┘    └────────────────────────────┘  │ │
│  └───────────────────────┬────────────────────────────────┘ │
│                          │                                   │
│  ┌───────────────────────▼────────────────────────────────┐ │
│  │                 Data Layer                              │ │
│  │  ┌──────────────┐  ┌───────────────┐  ┌─────────────┐ │ │
│  │  │SettingsRepo  │  │ Asset Files   │  │  BLE Stack  │ │ │
│  │  │ (DataStore)  │  │ (JSON/MP4)    │  │ (Android)   │ │ │
│  │  └──────────────┘  └───────────────┘  └─────────────┘ │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │ BLE
        ┌─────────────────┼─────────────────┐
        │                 │                 │
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ EffectStation│  │ ActionDrive 1│  │ ActionDrive 2│
│  4D_ES_XXXX  │  │  4D_AD1_XXXX │  │  4D_AD2_XXXX │
│              │  │              │  │              │
│ FAN/SPLASH   │  │   4-Pin      │  │   4-Pin      │
│ MIST/LED     │  │   Motor      │  │   Motor      │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 2.2 データフロー概要

```
[Asset Files]     [User Settings]
     │                   │
     ▼                   ▼
[TimelineParser]  [SettingsRepository]
     │                   │
     └─────────┬─────────┘
               ▼
      [PlaybackSyncEngine]
               │
               ├── ExoPlayer Position
               │
               ▼
      [Timeline Events]
               │
               ▼
      [CommandSender]
               │
               ▼
      [BleDeviceManager]
               │
        BLE Commands
               │
     ┌─────────┼─────────┐
     ▼         ▼         ▼
  [4D_ES]  [4D_AD1]  [4D_AD2]
```

---

## 3. 接続フロー

### 3.1 BLEスキャン開始

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   UI Layer  │     │BleDeviceManager│   │  BleScanner │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       │ startScanning()   │                   │
       │──────────────────>│                   │
       │                   │                   │
       │                   │ startScan(        │
       │                   │   SERVICE_UUID)   │
       │                   │──────────────────>│
       │                   │                   │
       │                   │                   │ BLE.startScan()
       │                   │                   │─────────┐
       │                   │                   │         │
       │                   │                   │<────────┘
       │                   │                   │
       │                   │   Flow<ScannedDevice>
       │                   │<──────────────────│
       │                   │                   │
       │  StateFlow<List>  │                   │
       │<──────────────────│                   │
       │                   │                   │
```

### 3.2 デバイス接続

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────┐
│   UI Layer  │     │ BleDeviceManager│     │  GATT Server │
└──────┬──────┘     └───────┬─────────┘     └──────┬───────┘
       │                    │                      │
       │ connect(address)   │                      │
       │───────────────────>│                      │
       │                    │                      │
       │                    │ connectGatt()        │
       │                    │─────────────────────>│
       │                    │                      │
       │                    │ onConnectionStateChange
       │                    │<─────────────────────│
       │                    │ (CONNECTED)          │
       │                    │                      │
       │                    │ discoverServices()   │
       │                    │─────────────────────>│
       │                    │                      │
       │                    │ onServicesDiscovered │
       │                    │<─────────────────────│
       │                    │                      │
       │                    │ Find SERVICE_UUID    │
       │                    │ Find COMMAND_CHAR    │
       │                    │ Find STATUS_CHAR     │
       │                    │──────────┐           │
       │                    │          │           │
       │                    │<─────────┘           │
       │                    │                      │
       │                    │ enableNotification() │
       │                    │─────────────────────>│
       │                    │                      │
       │ Flow<ConnectionState>                     │
       │<───────────────────│                      │
       │ (Connected)        │                      │
       │                    │                      │
```

### 3.3 接続状態遷移

```
                 ┌────────────────┐
                 │  DISCONNECTED  │
                 └───────┬────────┘
                         │ connect()
                         ▼
                 ┌────────────────┐
                 │   CONNECTING   │
                 └───────┬────────┘
                         │ onConnectionStateChange
           ┌─────────────┼─────────────┐
           │             │             │
           ▼             ▼             ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐
   │  CONNECTED │ │   FAILED   │ │   TIMEOUT  │
   └─────┬──────┘ └────────────┘ └────────────┘
         │
         │ onDisconnect / error
         ▼
┌────────────────────┐
│    DISCONNECTED    │
│  (auto-reconnect?) │
└────────────────────┘
```

---

## 4. 再生同期フロー

### 4.1 再生開始シーケンス

```
┌──────────┐  ┌───────────────┐  ┌────────────────┐  ┌──────────────┐
│ PlayScreen│  │PlaybackSyncEng│  │ TimelineParser │  │   ExoPlayer  │
└─────┬────┘  └───────┬───────┘  └───────┬────────┘  └──────┬───────┘
      │               │                  │                  │
      │ loadContent() │                  │                  │
      │──────────────>│                  │                  │
      │               │                  │                  │
      │               │ parseTimeline()  │                  │
      │               │─────────────────>│                  │
      │               │                  │                  │
      │               │ Timeline         │                  │
      │               │<─────────────────│                  │
      │               │                  │                  │
      │               │ setMediaItem()                      │
      │               │────────────────────────────────────>│
      │               │                                     │
      │               │ prepare()                           │
      │               │────────────────────────────────────>│
      │               │                                     │
      │ play()        │                                     │
      │──────────────>│                                     │
      │               │                                     │
      │               │ play()                              │
      │               │────────────────────────────────────>│
      │               │                                     │
      │               │ startSyncLoop()                     │
      │               │────────────┐                        │
      │               │            │                        │
      │               │<───────────┘                        │
      │               │                                     │
```

### 4.2 同期ループ

```
┌────────────────────────────────────────────────────────────────┐
│                    PlaybackSyncEngine                          │
│                                                                │
│    ┌──────────────────────────────────────────────────────┐   │
│    │              Sync Loop (16ms位置更新)                    │   │
│    │                                                       │   │
│    │  while (isPlaying) {                                  │   │
│    │      currentPosition = exoPlayer.currentPosition      │   │
│    │      lookAheadTime = currentPosition + LOOKAHEAD_MS   │   │
│    │                                                       │   │
│    │      // 同時刻のイベントをグループ化                    │   │
│    │      eventsByTime = events.groupBy { it.timestampMs } │   │
│    │                                                       │   │
│    │      for (event in timeline.events) {                 │   │
│    │          if (event.time <= lookAheadTime &&           │   │
│    │              !event.triggered) {                      │   │
│    │              // STOP→START最適化を適用                  │   │
│    │              optimizedEvents = optimizeStopStart()    │   │
│    │              scheduleEvent(event)                     │   │
│    │          }                                            │   │
│    │      }                                                │   │
│    │                                                       │   │
│    │      // 同時刻のイベント処理後、最小間隔を空ける           │   │
│    │      if (eventsAtTime.size > 1) {                     │   │
│    │          delay(MIN_COMMAND_INTERVAL_MS)  // 20ms      │   │
│    │      }                                                │   │
│    │  }                                                    │   │
│    └──────────────────────────────────────────────────────┘   │
│                                                                │
│    タイミング定数（250msイベント間隔に最適化）:                       │
│    LOOKAHEAD_MS = 200           // 先読み時間                   │
│    MIN_COMMAND_INTERVAL_MS = 20 // ESP32処理時間確保            │
│                                                                │
│    並列送信:                                                     │
│    - 異なるデバイス（Motor1 + Motor2）へは並列送信                  │
│    - 同一デバイスへは20ms間隔で順次送信                         │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 4.3 イベントスケジューリング

```
  Video Timeline
  ─────────────────────────────────────────────>
  0s        1s        2s        3s        4s

  │         │         │         │         │
  │    ▲    │         │    ▲    │         │
  │    │    │         │    │    │         │
  │    │    │         │    │    │         │

  Current   Lookahead      Future Events
  Position  Window         (not scheduled)
  1.2s      1.25s

  ┌─────────────────────────────────────────────┐
  │ Event at 1.2s: FAN,1                        │ → SCHEDULE NOW
  │ Event at 1.3s: LED,1,2,0,0                  │ → SCHEDULE NOW
  │ Event at 2.5s: MOTOR,STRONG                 │ → WAIT
  └─────────────────────────────────────────────┘
```

---

## 5. コマンド送信シーケンス

### 5.1 エフェクトコマンド送信

```
┌──────────────────┐  ┌─────────────┐  ┌────────────────┐  ┌───────────┐
│PlaybackSyncEngine│  │CommandSender│  │BleDeviceManager│  │ESP32 Device│
└────────┬─────────┘  └──────┬──────┘  └───────┬────────┘  └─────┬─────┘
         │                   │                 │                 │
         │ sendFanCommand(1) │                 │                 │
         │──────────────────>│                 │                 │
         │                   │                 │                 │
         │                   │ "FAN,1"         │                 │
         │                   │ formatCommand() │                 │
         │                   │─────────┐       │                 │
         │                   │         │       │                 │
         │                   │<────────┘       │                 │
         │                   │                 │                 │
         │                   │ writeCommand(   │                 │
         │                   │   EFFECT_STATION│                 │
         │                   │   "FAN,1")      │                 │
         │                   │────────────────>│                 │
         │                   │                 │                 │
         │                   │                 │ gatt.write(     │
         │                   │                 │   COMMAND_CHAR, │
         │                   │                 │   "FAN,1")      │
         │                   │                 │────────────────>│
         │                   │                 │                 │
         │                   │                 │ onWriteComplete │
         │                   │                 │<────────────────│
         │                   │                 │                 │
         │                   │ Result.Success  │                 │
         │                   │<────────────────│                 │
         │                   │                 │                 │
         │ Result.Success    │                 │                 │
         │<──────────────────│                 │                 │
         │                   │                 │                 │
```

### 5.2 コマンドターゲット解決

```kotlin
// PlaybackSyncEngine内での処理

fun processAction(action: EventAction, event: TimelineEvent) {
    when (action.action) {
        ActionType.FAN -> 
            commandSender.sendTo(DeviceType.EFFECT_STATION, "FAN,${action.value}")
        
        ActionType.SPLASH -> 
            commandSender.sendTo(DeviceType.EFFECT_STATION, "SPLASH")
        
        ActionType.MIST -> 
            commandSender.sendTo(DeviceType.EFFECT_STATION, "MIST,${action.mode}")
        
        ActionType.LED ->
            commandSender.sendTo(DeviceType.EFFECT_STATION, 
                "LED,${action.colorId},${action.brightness},${action.effect},${action.transition}")
        
        ActionType.VIBRATION -> {
            val target = when (action.target) {
                "MOTOR1" -> DeviceType.ACTION_DRIVE_1
                "MOTOR2" -> DeviceType.ACTION_DRIVE_2
                else -> DeviceType.ACTION_DRIVE_1
            }
            commandSender.sendTo(target, "MOTOR,${action.mode}")
        }
    }
}
```

### 5.3 マルチデバイス同時送信

```
   ┌───────────────┐
   │CommandSender  │
   └───────┬───────┘
           │
           │ sendToAll("OFF")
           │
     ┌─────┼─────┬─────────────┐
     │     │     │             │
     ▼     ▼     ▼             ▼
  ┌─────┐ ┌─────┐ ┌─────┐
  │ES   │ │AD1  │ │AD2  │
  │OFF  │ │OFF  │ │OFF  │
  └─────┘ └─────┘ └─────┘

Note: BLEは同時書き込み非推奨のため、
      実際には順次送信 (キュー管理)
```

---

## 6. エラーハンドリング

### 6.1 BLE接続エラー

```
┌──────────────────────────────────────────────────────────┐
│                  Error Handling Flow                      │
├──────────────────────────────────────────────────────────┤
│                                                          │
│   ┌─────────────┐                                        │
│   │ BLE Error   │                                        │
│   └──────┬──────┘                                        │
│          │                                               │
│          ▼                                               │
│   ┌─────────────────┐                                    │
│   │ Classify Error  │                                    │
│   └──────┬──────────┘                                    │
│          │                                               │
│    ┌─────┼─────┬─────────┬─────────┐                    │
│    ▼     ▼     ▼         ▼         ▼                    │
│ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐          │
│ │Timeout│ │Disconnect│ │Service│ │Write  │ │Other │   │
│ │      │ │      │ │NotFound│ │Failed │ │     │          │
│ └───┬──┘ └───┬──┘ └───┬──┘ └───┬──┘ └───┬──┘          │
│     │        │        │        │        │               │
│     ▼        ▼        ▼        ▼        ▼               │
│   Auto-    Auto-    Log &    Retry   Log &             │
│  Reconnect Reconnect Skip    (3x)    Report            │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 6.2 エラー種別と対処

| エラー種別 | 原因 | 対処 |
|-----------|------|------|
| `ScanFailed` | BLEスキャン失敗 | 再スキャン / Bluetooth再起動 |
| `ConnectionTimeout` | 接続タイムアウト | 自動リトライ (3回) |
| `ServiceNotFound` | サービスUUID不一致 | デバイス確認 / ファームウェア更新 |
| `CharacteristicNotFound` | Characteristic不在 | ファームウェア確認 |
| `WriteFailed` | 書き込み失敗 | リトライ / 再接続 |
| `Disconnected` | 意図しない切断 | 自動再接続 |

### 6.3 再生中のエラー処理

```kotlin
// PlaybackSyncEngine内

private fun handleCommandError(error: BleError, event: TimelineEvent) {
    when (error) {
        is BleError.WriteFailed -> {
            // コマンド失敗をログに記録
            Log.w(TAG, "Command failed for event at ${event.time}ms: ${error.message}")
            
            // 再生は継続 (エフェクト欠落を許容)
            // 重要: 動画再生を止めない
        }
        
        is BleError.Disconnected -> {
            // 切断通知をUIに表示
            _playbackState.value = PlaybackState.DeviceDisconnected(error.deviceAddress)
            
            // バックグラウンドで再接続試行
            scope.launch {
                bleDeviceManager.reconnect(error.deviceAddress)
            }
        }
    }
}
```

---

## 7. 設定データフロー

### 7.1 設定の保存と適用

```
┌──────────────┐     ┌────────────────┐     ┌───────────────┐
│SettingsScreen│     │SettingsReposiory│    │   DataStore   │
└──────┬───────┘     └───────┬────────┘     └───────┬───────┘
       │                     │                      │
       │ updateSetting(key,  │                      │
       │   value)            │                      │
       │────────────────────>│                      │
       │                     │                      │
       │                     │ dataStore.edit {     │
       │                     │   prefs[key] = value │
       │                     │ }                    │
       │                     │─────────────────────>│
       │                     │                      │
       │                     │                      │ Persist
       │                     │                      │─────────┐
       │                     │                      │         │
       │                     │                      │<────────┘
       │                     │                      │
       │                     │ Flow emit            │
       │                     │<─────────────────────│
       │                     │                      │
       │ StateFlow update    │                      │
       │<────────────────────│                      │
       │                     │                      │
```

### 7.2 設定項目と影響範囲

| 設定カテゴリ | 設定項目 | 影響するコンポーネント |
|-------------|---------|---------------------|
| **接続** | 自動再接続 | BleDeviceManager |
| | スキャンタイムアウト | BleScanner |
| **再生** | 先読み時間 | PlaybackSyncEngine |
| | 同期間隔 | PlaybackSyncEngine |
| **エフェクト** | FAN有効 | CommandSender |
| | SPLASH有効 | CommandSender |
| | MIST有効 | CommandSender |
| | LED有効 | CommandSender |
| | VIBRATION有効 | CommandSender |
| **安全** | 最大振動時間 | PlaybackSyncEngine |
| | 切断時停止 | BleDeviceManager |

### 7.3 設定の即時反映

```kotlin
// SettingsRepository を監視

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playbackSyncEngine: PlaybackSyncEngine
) : ViewModel() {
    
    init {
        viewModelScope.launch {
            settingsRepository.effectSettings.collect { settings ->
                playbackSyncEngine.updateEffectFilters(
                    fanEnabled = settings.fanEnabled,
                    splashEnabled = settings.splashEnabled,
                    mistEnabled = settings.mistEnabled,
                    ledEnabled = settings.ledEnabled,
                    vibrationEnabled = settings.vibrationEnabled
                )
            }
        }
    }
}
```

---

## 8. アプリケーションライフサイクル

### 8.1 起動シーケンス

```
┌─────────────────────────────────────────────────────────────────┐
│                       App Launch Sequence                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Application.onCreate()                                       │
│     └─ Hilt: Initialize DI Graph                                │
│                                                                  │
│  2. MainActivity.onCreate()                                      │
│     └─ Compose: Set Content                                     │
│     └─ Request Bluetooth Permission                             │
│                                                                  │
│  3. AppNavigation Composable                                     │
│     └─ Initialize NavController                                 │
│     └─ Display NavigationRail                                   │
│                                                                  │
│  4. HomeScreen (Default Destination)                            │
│     └─ Observe DevicesViewModel                                 │
│     └─ Start BLE Scan (if auto-scan enabled)                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 バックグラウンド遷移

```
┌──────────────────────────────────────────────────────────────┐
│                  Background Transition                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  onPause() triggered                                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                                                        │ │
│  │  if (isPlaying) {                                      │ │
│  │      // オプション: 再生を継続 or 一時停止              │ │
│  │      when (settings.backgroundBehavior) {              │ │
│  │          PAUSE -> pausePlayback()                      │ │
│  │          CONTINUE -> keepPlaying()  // PiP等          │ │
│  │          STOP -> stopAndDisconnect()                   │ │
│  │      }                                                 │ │
│  │  }                                                     │ │
│  │                                                        │ │
│  │  // BLE接続は維持 (再生継続の場合)                     │ │
│  │  // または切断 (設定による)                            │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 8.3 終了シーケンス

```
┌─────────────────────────────────────────────────────────────────┐
│                      App Termination Sequence                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  onDestroy() triggered                                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                                                            │ │
│  │  1. PlaybackSyncEngine.stop()                              │ │
│  │     ├─ Send "ALL_OFF" to all devices                       │ │
│  │     ├─ Cancel sync coroutine                               │ │
│  │     └─ Release ExoPlayer                                   │ │
│  │                                                            │ │
│  │  2. BleDeviceManager.disconnectAll()                       │ │
│  │     ├─ Close all GATT connections                          │ │
│  │     └─ Clear connection state                              │ │
│  │                                                            │ │
│  │  3. BleScanner.stopScan()                                  │ │
│  │     └─ Stop any active BLE scan                            │ │
│  │                                                            │ │
│  │  4. Hilt: Cleanup Singletons                               │ │
│  │                                                            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. データフォーマット相互参照

### 9.1 タイムライン → コマンド変換表

| Timeline JSON | Android Enum | ESP32 Command | 対象デバイス |
|---------------|--------------|---------------|-------------|
| `"action": "FAN"` + `"value": 1` | ActionType.FAN | `FAN,1` | EFFECT_STATION |
| `"action": "FAN"` + `"value": 0` | ActionType.FAN | `FAN,0` | EFFECT_STATION |
| `"action": "SPLASH"` | ActionType.SPLASH | `SPLASH` | EFFECT_STATION |
| `"action": "MIST"` + `"mode": "SHOT"` | ActionType.MIST | `MIST,1` | EFFECT_STATION |
| `"action": "MIST"` + `"mode": "START"` | ActionType.MIST | `MIST,2` | EFFECT_STATION |
| `"action": "MIST"` + `"mode": "OFF"` | ActionType.MIST | `MIST,0` | EFFECT_STATION |
| `"action": "LED"` + params | ActionType.LED | `LED,colorId,brightness,effect,transition` | EFFECT_STATION |
| `"action": "VIBRATION"` + `"target": "MOTOR1"` | ActionType.VIBRATION | `MOTOR,mode` | ACTION_DRIVE_1 |
| `"action": "VIBRATION"` + `"target": "MOTOR2"` | ActionType.VIBRATION | `MOTOR,mode` | ACTION_DRIVE_2 |
| `"action": "ALL_OFF"` | ActionType.ALL_OFF | `OFF` | ALL |

### 9.2 LED パラメータマッピング

```
Timeline JSON:
{
    "action": "LED",
    "colorId": 1,       // 0-11
    "brightness": 2,    // 0=OFF, 1=弱, 2=強
    "effect": 0,        // 0=点灯, 1=点滅, 2=呼吸
    "transition": 1     // 0=一瞬, 1=フェード
}

                    ↓ TimelineParser ↓

Kotlin EventAction:
EventAction(
    action = ActionType.LED,
    colorId = 1,
    brightness = 2,
    effect = 0,
    transition = 1
)

                    ↓ CommandSender ↓

BLE Command String:
"LED,1,2,0,1"

                    ↓ BLE Write ↓

ESP32 (effect_station):
setLedColor(1, 2, 0, 1)
→ colors[1] (赤), brightness=2 (強), effect=0 (点灯), transition=1 (フェード)
```

### 9.3 振動モードマッピング

```
Timeline JSON:
{
    "action": "VIBRATION",
    "target": "MOTOR1",
    "mode": "STRONG"
}

                    ↓ TimelineParser ↓

Kotlin EventAction:
EventAction(
    action = ActionType.VIBRATION,
    target = "MOTOR1",
    mode = VibrationMode.STRONG
)

                    ↓ CommandSender ↓

Device Target Resolution:
target = "MOTOR1" → DeviceType.ACTION_DRIVE_1

BLE Command String:
VibrationMode.STRONG.esp32Command → "MOTOR,STRONG"

                    ↓ BLE Write ↓

ESP32 (action_drive_motor1):
processStringCommand("MOTOR,STRONG")
→ setMotorStrong()
→ All 4 pins HIGH
```

---

## 付録: シーケンス図まとめ

### A. 完全再生フロー

```
User          App            Engine         Sender         BLE            ESP32
 │             │               │              │              │               │
 │  Select     │               │              │              │               │
 │  Content    │               │              │              │               │
 │────────────>│               │              │              │               │
 │             │ loadContent   │              │              │               │
 │             │──────────────>│              │              │               │
 │             │               │              │              │               │
 │             │               │ parse JSON   │              │               │
 │             │               │─────┐        │              │               │
 │             │               │     │        │              │               │
 │             │               │<────┘        │              │               │
 │             │               │              │              │               │
 │             │               │ prepare video│              │               │
 │             │               │─────┐        │              │               │
 │             │               │     │        │              │               │
 │             │               │<────┘        │              │               │
 │             │               │              │              │               │
 │  Press Play │               │              │              │               │
 │────────────>│               │              │              │               │
 │             │ play()        │              │              │               │
 │             │──────────────>│              │              │               │
 │             │               │              │              │               │
 │             │               │ ─── Sync Loop ────────────────────────────  │
 │             │               │ │                                        │  │
 │             │               │ │  Check position                        │  │
 │             │               │ │  Find events                           │  │
 │             │               │ │            │              │            │  │
 │             │               │ │  Event!    │              │            │  │
 │             │               │ │───────────>│              │            │  │
 │             │               │ │            │              │            │  │
 │             │               │ │            │ format cmd   │            │  │
 │             │               │ │            │─────┐        │            │  │
 │             │               │ │            │     │        │            │  │
 │             │               │ │            │<────┘        │            │  │
 │             │               │ │            │              │            │  │
 │             │               │ │            │ writeCommand │            │  │
 │             │               │ │            │─────────────>│            │  │
 │             │               │ │            │              │            │  │
 │             │               │ │            │              │ BLE write  │  │
 │             │               │ │            │              │───────────>│  │
 │             │               │ │            │              │            │  │
 │             │               │ │            │              │ Execute    │  │
 │             │               │ │            │              │ Effect     │  │
 │             │               │ │            │              │<─ ─ ─ ─ ─ ─│  │
 │             │               │ │                                        │  │
 │             │               │ └────────────────────────────────────────┘  │
 │             │               │              │              │               │
 │  Video End  │               │              │              │               │
 │<────────────│               │              │              │               │
 │             │               │              │              │               │
 │             │               │ stop         │              │               │
 │             │               │─────────────>│              │               │
 │             │               │              │              │               │
 │             │               │              │ ALL_OFF      │               │
 │             │               │              │─────────────>│               │
 │             │               │              │              │───────────>│  │
 │             │               │              │              │            │  │
```
