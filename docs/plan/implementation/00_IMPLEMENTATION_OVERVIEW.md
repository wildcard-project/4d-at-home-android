# 4D@HOME Android アプリ 実装計画書

**バージョン**: 1.0.0  
**作成日**: 2026年1月30日  
**プロジェクト名**: 4d-at-home-android

---

## 📑 目次

1. [プロジェクト概要](#1-プロジェクト概要)
2. [システム構成](#2-システム構成)
3. [技術スタック](#3-技術スタック)
4. [Phase一覧](#4-phase一覧)
5. [デバイス仕様](#5-デバイス仕様)
6. [BLEプロトコル仕様](#6-bleプロトコル仕様)
7. [エフェクト定義](#7-エフェクト定義)
8. [ファイル構成](#8-ファイル構成)
9. [実装優先順位](#9-実装優先順位)
10. [リスクと対策](#10-リスクと対策)

---

## 1. プロジェクト概要

### 1.1 目的

Web版「4DX@HOME」システムをAndroidネイティブアプリ化し、**Bluetooth Low Energy (BLE)** を用いてESP32デバイスを直接制御する。Raspberry PiやCloud Runを介さず、**スタンドアロン**で4Dエフェクト体験を提供する。

### 1.2 主要機能

| 機能 | 説明 |
|:-----|:-----|
| **BLEデバイス管理** | EffectStation・ActionDriveの検出・接続・管理 |
| **エフェクトマッピング** | エフェクトタイプとデバイスの関連付け |
| **手動エフェクト制御** | 開発・デモ用の手動トリガー |
| **動画再生同期** | JSONタイムラインに基づく自動エフェクト発火 |
| **没入型UI** | ダークテーマ・横画面・Navigation Rail |

### 1.3 対象デバイス

| デバイス | BLE名パターン | 機能 | ESP32台数 |
|:---------|:-------------|:-----|:---------:|
| **EffectStation** | `4D_ES_XXXX` | 風・水・ミスト・LED(RGBW) | 1台 |
| **ActionDrive Motor1** | `4D_AD1_XXXX` | 振動モーター4個（背中） | 1台 |
| **ActionDrive Motor2** | `4D_AD2_XXXX` | 振動モーター4個（お尻） | 1台 |

---

## 2. システム構成

### 2.1 アーキテクチャ図

```
┌─────────────────────────────────────────────────────────────────┐
│                      Android アプリケーション                     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    UI Layer (Jetpack Compose)              │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐          │  │
│  │  │ PlaybackScreen│ │ControlScreen│ │SettingsScreen│         │  │
│  │  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘          │  │
│  └─────────┼───────────────┼───────────────┼─────────────────┘  │
│            │               │               │                     │
│  ┌─────────▼───────────────▼───────────────▼─────────────────┐  │
│  │                  ViewModel Layer                           │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐          │  │
│  │  │PlaybackVM   │ │ControlVM   │ │SettingsVM  │          │  │
│  │  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘          │  │
│  └─────────┼───────────────┼───────────────┼─────────────────┘  │
│            │               │               │                     │
│  ┌─────────▼───────────────▼───────────────▼─────────────────┐  │
│  │                  Domain Layer                              │  │
│  │  ┌──────────────────┐  ┌──────────────────┐               │  │
│  │  │ TimelineProcessor │  │   EffectRouter   │               │  │
│  │  │ (JSON解析・同期)   │  │ (エフェクト振分) │               │  │
│  │  └────────┬─────────┘  └────────┬─────────┘               │  │
│  └───────────┼─────────────────────┼─────────────────────────┘  │
│              │                     │                             │
│  ┌───────────▼─────────────────────▼─────────────────────────┐  │
│  │                   BLE Layer                                │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐          │  │
│  │  │  BleScanner │ │BleDeviceMgr │ │ CommandSender│          │  │
│  │  └─────────────┘ └─────────────┘ └──────┬──────┘          │  │
│  └─────────────────────────────────────────┼─────────────────┘  │
└────────────────────────────────────────────┼─────────────────────┘
                                             │ BLE
        ┌────────────────────────────────────┼────────────────────┐
        │                                    │                    │
        ▼                                    ▼                    ▼
┌───────────────┐                 ┌───────────────┐    ┌───────────────┐
│ EffectStation │                 │ ActionDrive   │    │ ActionDrive   │
│  4D_ES_XXXX   │                 │   Motor1      │    │   Motor2      │
│               │                 │ 4D_AD1_XXXX   │    │ 4D_AD2_XXXX   │
│ ┌───────────┐ │                 │ ┌───────────┐ │    │ ┌───────────┐ │
│ │  ESP32    │ │                 │ │  ESP32    │ │    │ │  ESP32    │ │
│ └───────────┘ │                 │ └───────────┘ │    │ └───────────┘ │
│ 風│水│ミスト│LED│                 │ モーター×4  │    │ モーター×4  │
└───────────────┘                 └───────────────┘    └───────────────┘
```

### 2.2 Web版との差異

| 項目 | Web版 | Android版 |
|:-----|:------|:----------|
| **通信方式** | Wi-Fi + MQTT / WebSocket | Bluetooth LE |
| **中継サーバー** | Raspberry Pi + Cloud Run | なし（直接通信） |
| **動画再生** | ブラウザ | ExoPlayer (Media3) |
| **UI** | React + Tailwind | Jetpack Compose |
| **デバイス認証** | セッションID | BLEペアリング |

---

## 3. 技術スタック

### 3.1 Android アプリ

| 項目 | 技術/バージョン |
|:-----|:----------------|
| **言語** | Kotlin 2.0+ |
| **最小SDK** | Android 8.0 (API 26) |
| **ターゲットSDK** | Android 15 (API 35) |
| **UIフレームワーク** | Jetpack Compose + Material 3 |
| **ナビゲーション** | Navigation Compose (Navigation Rail) |
| **非同期処理** | Kotlin Coroutines + Flow |
| **DI** | Hilt |
| **動画プレイヤー** | Media3 (ExoPlayer) |
| **BLE** | Android BLE API + Coroutines |
| **アーキテクチャ** | MVVM + Clean Architecture |

### 3.2 ESP32 ファームウェア

| 項目 | 技術/バージョン |
|:-----|:----------------|
| **開発環境** | PlatformIO |
| **フレームワーク** | Arduino (ESP32) |
| **BLEライブラリ** | ESP32 BLE Arduino |
| **LED制御** | Adafruit NeoPixel (EffectStation) |
| **ボード** | ESP32-WROOM-32 / ESP32-DevKitC |

---

## 4. Phase一覧

| Phase | 名称 | 期間目安 | 主要成果物 |
|:-----:|:-----|:--------:|:-----------|
| **1** | プロジェクトセットアップ | 1日 | Gradleビルド、Hilt設定、テーマ |
| **2** | BLE通信レイヤー | 2-3日 | BleScanner, BleDeviceManager |
| **3** | EffectStation実装 | 3-4日 | ESP32 BLEファームウェア + Android制御 |
| **4** | ActionDrive実装 | 2-3日 | ESP32×2 BLEファームウェア + Android制御 |
| **5** | 動画再生・同期エンジン | 3-4日 | ExoPlayer + TimelineProcessor |
| **6** | 統合・テスト・仕上げ | 2-3日 | 全体テスト、UI調整、ドキュメント |

**合計見込み**: 約2-3週間

### 詳細ドキュメント

- [Phase 1: プロジェクトセットアップ](./01_PHASE1_PROJECT_SETUP.md)
- [Phase 2: BLE通信レイヤー](./02_PHASE2_BLE_LAYER.md)
- [Phase 3: EffectStation実装](./03_PHASE3_EFFECT_STATION.md)
- [Phase 4: ActionDrive実装](./04_PHASE4_ACTION_DRIVE.md)
- [Phase 5: 動画再生・同期エンジン](./05_PHASE5_PLAYBACK_SYNC.md)
- [Phase 6: 統合・テスト・仕上げ](./06_PHASE6_INTEGRATION.md)

---

## 5. デバイス仕様

### 5.1 EffectStation (4D_ES_XXXX)

**ハードウェア構成**（4DHOME_STATION_CONTROL.ino準拠）

| GPIO | 機能 | 制御方式 |
|:----:|:-----|:---------|
| 25 | FAN（風） | ON/OFF |
| 26 | SPLASH（水しぶき） | 一瞬パルス (200ms) |
| 32 | MIST（ミスト） | トグル制御 |
| 27 | LED（RGBW NeoPixel） | 色・明るさ・エフェクト |

**LED色テーブル**

| ID | 色名 | RGB値 |
|:--:|:-----|:------|
| 0 | ピンク | (255, 20, 100) |
| 1 | 赤 | (255, 0, 0) |
| 2 | オレンジ | (255, 100, 0) |
| 3 | 黄色 | (255, 255, 0) |
| 4 | 黄緑 | (150, 255, 0) |
| 5 | 緑 | (0, 255, 0) |
| 6 | 深緑 | (0, 100, 0) |
| 7 | 水色 | (0, 255, 255) |
| 8 | 青 | (0, 0, 255) |
| 9 | 紫 | (150, 0, 255) |
| 10 | 白 | (0, 0, 0, 255) |
| 11 | 消灯 | (0, 0, 0, 0) |

### 5.2 ActionDrive Motor1/Motor2 (4D_AD1_XXXX / 4D_AD2_XXXX)

**ハードウェア構成**（4DX_MOTOR_MQTT.ino準拠）

| GPIO (D番号) | 実GPIO | 振動強度 |
|:------------:|:------:|:---------|
| D5 | 14 | STRONG（強） |
| D6 | 12 | MEDIUM_STRONG（中強） |
| D7 | 13 | MEDIUM_WEAK（中弱） |
| D8 | 15 | WEAK（弱） |

**振動モード**

| モード | 説明 | 動作ピン |
|:-------|:-----|:---------|
| OFF | 停止 | 全OFF |
| WEAK | 弱振動 | D8のみ |
| MEDIUM_WEAK | 中弱振動 | D7のみ |
| MEDIUM_STRONG | 中強振動 | D6 + D7 |
| STRONG | 強振動 | 全ON |
| HEARTBEAT | 心拍パターン | D7 → D5 (ドッ..クン) |
| RUMBLE_FAST | 速い振動 | 全ON/OFF 150ms |
| RUMBLE_SLOW | 遅い振動 | 全ON/OFF 300ms |

---

## 6. BLEプロトコル仕様

### 6.1 GATT構成

```
GATT Server (ESP32)
│
└── Service: 4D580001-0000-1000-8000-00805F9B34FB
    │   名称: 4DX Effect Service
    │
    ├── Characteristic: 4D580002-... (Command)
    │   プロパティ: Write, Write No Response
    │   用途: コマンド送信
    │
    └── Characteristic: 4D580003-... (Status)
        プロパティ: Read, Notify
        用途: ステータス通知
        └── Descriptor: CCCD (00002902-...)
```

### 6.2 コマンド体系

#### EffectStation コマンド

| コマンド | 説明 | 例 |
|:---------|:-----|:---|
| `FAN,1` | 風ON | `FAN,0` で OFF |
| `SPLASH` | 水しぶき発射 | 自動で200ms後OFF |
| `MIST,0/1/2` | ミスト OFF/一瞬/継続 | |
| `LED,色ID,強さ,効果,遷移` | LED制御 | `LED,1,2,0,0` = 赤・強・点灯・即時 |

#### ActionDrive コマンド

| コマンド | 説明 |
|:---------|:-----|
| `OFF` | 振動停止 |
| `WEAK` | 弱振動 |
| `MEDIUM_WEAK` | 中弱振動 |
| `MEDIUM_STRONG` | 中強振動 |
| `STRONG` | 強振動 |
| `HEARTBEAT` | 心拍パターン |
| `RUMBLE_FAST` | 速い振動 |
| `RUMBLE_SLOW` | 遅い振動 |

### 6.3 ステータス通知

| ステータス | 意味 |
|:-----------|:-----|
| `CONNECTED` | 接続成功 |
| `FAN:ON` / `FAN:OFF` | 風の状態 |
| `SPLASH:FIRED` | 水しぶき発射 |
| `MIST:ON` / `MIST:OFF` | ミストの状態 |
| `LED:色ID` | LED色変更 |
| `MOTOR:モード` | 振動モード変更 |
| `ERR:UNKNOWN` | 不明なコマンド |

---

## 7. エフェクト定義

### 7.1 EffectType（Android側定義）

```kotlin
enum class EffectType(
    val jsonKey: String,
    val displayName: String,
    val emoji: String,
    val targetDevice: DeviceType
) {
    // EffectStation
    FAN("fan", "風", "💨", DeviceType.EFFECT_STATION),
    SPLASH("splash", "水しぶき", "💦", DeviceType.EFFECT_STATION),
    MIST("mist", "ミスト", "🌫️", DeviceType.EFFECT_STATION),
    LED("led", "LED", "💡", DeviceType.EFFECT_STATION),
    
    // ActionDrive (共通制御)
    VIBRATION("vibration", "振動", "📳", DeviceType.ACTION_DRIVE_BOTH),
    
    // ActionDrive (個別制御)
    MOTOR1("motor1", "振動(背中)", "🔙", DeviceType.ACTION_DRIVE_1),
    MOTOR2("motor2", "振動(お尻)", "🪑", DeviceType.ACTION_DRIVE_2),
}

enum class DeviceType {
    EFFECT_STATION,
    ACTION_DRIVE_1,
    ACTION_DRIVE_2,
    ACTION_DRIVE_BOTH
}
```

### 7.2 JSONタイムライン形式

**JSON_SPECIFICATION.md に完全準拠**

```json
{
  "events": [
    {
      "t": 0.0,
      "action": "caption",
      "text": "シーンの説明文"
    },
    {
      "t": 5.0,
      "action": "start",
      "effect": "wind",
      "mode": "burst"
    },
    {
      "t": 10.0,
      "action": "stop",
      "effect": "wind",
      "mode": "burst"
    },
    {
      "t": 15.5,
      "action": "shot",
      "effect": "water",
      "mode": "burst"
    },
    {
      "t": 20.0,
      "action": "start",
      "effect": "color",
      "mode": "red"
    },
    {
      "t": 25.0,
      "action": "stop",
      "effect": "color",
      "mode": "red"
    },
    {
      "t": 25.0,
      "action": "start",
      "effect": "flash",
      "mode": "fast_blink"
    },
    {
      "t": 26.0,
      "action": "stop",
      "effect": "flash",
      "mode": "fast_blink"
    },
    {
      "t": 30.0,
      "action": "start",
      "effect": "vibration",
      "mode": "up_down_strong"
    },
    {
      "t": 32.0,
      "action": "stop",
      "effect": "vibration",
      "mode": "up_down_strong"
    },
    {
      "t": 35.0,
      "action": "start",
      "effect": "mist",
      "mode": "burst"
    },
    {
      "t": 40.0,
      "action": "stop",
      "effect": "mist",
      "mode": "burst"
    }
  ]
}
```

### 7.3 効果（effect）とモード（mode）マッピング

| effect | mode | 対象デバイス | 説明 |
|:-------|:-----|:-------------|:-----|
| `vibration` | `up_weak` / `up_mid_weak` / `up_mid_strong` / `up_strong` | ActionDrive Motor1 | 背中振動 |
| `vibration` | `down_weak` / `down_mid_weak` / `down_mid_strong` / `down_strong` | ActionDrive Motor2 | お尻振動 |
| `vibration` | `up_down_weak` / `up_down_mid_weak` / `up_down_mid_strong` / `up_down_strong` | 両モーター | 上下同時 |
| `vibration` | `heartbeat` | 両モーター | 心拍パターン |
| `flash` | `steady` / `slow_blink` / `fast_blink` | EffectStation LED | 閃光効果 |
| `color` | `red` / `green` / `blue` / `yellow` / `cyan` / `purple` | EffectStation LED | 環境照明 |
| `water` | `burst` | EffectStation SPLASH | 水しぶき（shot専用） |
| `wind` | `burst` | EffectStation FAN | 風 |
| `mist` | `burst` | EffectStation MIST | ミスト（新規追加） |

---

## 8. ファイル構成

### 8.1 Androidアプリ

```
app/src/main/java/com/wildcard/fourd_at_home/
├── FourdAtHomeApplication.kt          # Hilt Application
├── MainActivity.kt                     # メインActivity
│
├── ui/                                 # UI Layer
│   ├── theme/                          # Material 3 テーマ
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── navigation/                     # ナビゲーション
│   │   └── AppNavigation.kt
│   ├── components/                     # 共通コンポーネント
│   │   ├── NavigationRail.kt
│   │   └── StatusIndicator.kt
│   ├── playback/                       # 再生画面
│   │   ├── PlaybackScreen.kt
│   │   └── PlaybackViewModel.kt
│   ├── control/                        # 制御画面
│   │   ├── ControlScreen.kt
│   │   └── ControlViewModel.kt
│   └── settings/                       # 設定画面
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
│
├── domain/                             # Domain Layer
│   ├── model/                          # ドメインモデル
│   │   ├── EffectType.kt
│   │   ├── EffectEvent.kt
│   │   └── DeviceInfo.kt
│   ├── timeline/                       # タイムライン処理
│   │   ├── TimelineProcessor.kt
│   │   └── TimelineParser.kt
│   ├── effect/                         # エフェクト制御
│   │   ├── EffectRouter.kt
│   │   └── EffectCommandResolver.kt
│   └── repository/                     # リポジトリ
│       └── MappingRepository.kt
│
├── ble/                                # BLE Layer
│   ├── BleConstants.kt
│   ├── BleScanner.kt
│   ├── BleDeviceManager.kt
│   ├── BleConnection.kt
│   └── CommandSender.kt
│
├── data/                               # Data Layer
│   └── local/
│       └── PreferencesManager.kt
│
└── di/                                 # Dependency Injection
    ├── AppModule.kt
    └── BleModule.kt
```

### 8.2 ESP32ファームウェア

```
esp32/
├── effect_station/                     # EffectStation用
│   ├── platformio.ini
│   └── src/
│       └── main.cpp
│
├── action_drive_motor1/                # ActionDrive Motor1用
│   ├── platformio.ini
│   └── src/
│       └── main.cpp
│
└── action_drive_motor2/                # ActionDrive Motor2用
    ├── platformio.ini
    └── src/
        └── main.cpp
```

---

## 9. 実装優先順位

### 優先度: 高 (Phase 1-3)

1. **プロジェクトセットアップ** - 開発基盤
2. **BLE通信レイヤー** - 全デバイス共通
3. **EffectStation** - 優先デバイス

### 優先度: 中 (Phase 4-5)

4. **ActionDrive** - 2番目のデバイス
5. **動画再生・同期** - メイン機能

### 優先度: 低 (Phase 6)

6. **統合・仕上げ** - 品質向上

---

## 10. リスクと対策

| リスク | 影響 | 対策 |
|:-------|:-----|:-----|
| BLE接続不安定 | 全機能 | 再接続ロジック、接続状態監視 |
| コマンド遅延 | 同期ずれ | タイムスタンプ補正、バッファリング |
| 複数デバイス管理 | 複雑化 | デバイスマネージャーの設計 |
| ESP32メモリ不足 | BLE動作 | 最適化、不要機能削除 |
| Android権限 | BLE使用不可 | 権限ハンドリング、ガイダンスUI |

---

## 関連ドキュメント

- [REQUIREMENTS.md](../REQUIREMENTS.md) - 要件定義書
- [bluetooth_test/](../bluetooth_test/) - BLEテスト版仕様書群
- [Web_4DXatHOME/](../Web_4DXatHOME/) - Web版仕様書群
- [4DHOME_STATION_CONTROL.ino](../4DHOME_STATION_CONTROL/4DHOME_STATION_CONTROL.ino) - EffectStationファームウェア

---

**次のステップ**: [Phase 1: プロジェクトセットアップ](./01_PHASE1_PROJECT_SETUP.md) へ進む
