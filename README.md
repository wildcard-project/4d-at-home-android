# 4D@HOME Android - あなたのおうちで「最高の映像」が「最強の体験」へ。

<div align="center">

[![4DX@HOME デモ動画](https://img.youtube.com/vi/t1n5mQZU_nM/maxresdefault.jpg)](https://youtu.be/t1n5mQZU_nM)

**クリックして動画を再生**

[![YouTube](https://img.shields.io/badge/▶%20YouTube-紹介動画を見る-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://youtu.be/t1n5mQZU_nM)

---

### 🏆 JPHACKS 2025 受賞

[![JPHacks 2025](https://img.shields.io/badge/JPHacks%202025-Best%20Hackday%20Award-gold?style=for-the-badge&logo=trophy)](https://jphacks.com/)
[![JPHacks 2025](https://img.shields.io/badge/審査委員特別賞-silver?style=for-the-badge&logo=award)](https://jphacks.com/)
[![JPHacks 2025](https://img.shields.io/badge/Innovator認定-purple?style=for-the-badge&logo=lightbulb)](https://jphacks.com/)

</div>

---

## 📋 目次

- [製品概要](#製品概要)
  - [背景](#背景製品開発のきっかけ)
  - [製品説明](#製品説明)
  - [Web版との違い](#web版との違い)
- [機能](#機能)
- [システム構成](#システム構成)
- [技術スタック](#技術スタック)
- [対応エフェクト](#対応エフェクト)
- [タイムラインJSON形式](#タイムラインjson形式)
- [BLEプロトコル](#bleプロトコル)
- [ビルド手順](#ビルド手順)
- [使用方法](#使用方法)
- [ドキュメント](#ドキュメント)
- [今後の展望](#今後の展望)

---

## 製品概要

### 背景（製品開発のきっかけ）

本プロジェクトは、「**没入体験の格差**」を解消することを目的としています。

#### 解決すべき3つの格差

1. **身体的格差**  
   全世界の5人〜6人に1人が視覚、聴覚、歩行のいずれかに重い困難を抱えており、既存の4DXシアターでは利用制限が存在します。

2. **地域の格差**  
   地方では映画館の閉館が進行しています。例えば石川県は人口あたりの映画館数が日本一ですが、すべて金沢市近辺に集中しており、遠方からのアクセスは困難です。

3. **趣味・コンテンツの格差**  
   既存の特殊シアターは「アクション」や「人気IPコンテンツ」など特定ジャンルに偏りがちであり、上映期間も限られています。

### 製品説明

**4D@HOME Android** は、家庭で4DX体験を再現するAndroidアプリケーションです。

BLE（Bluetooth Low Energy）を使用してESP32デバイスと**直接通信**し、動画再生に同期したエフェクト（風、水、ミスト、LED照明、振動）を制御します。

従来の「観る」体験を「体感する」次元へと押し上げ、リビングを本格的な**4DXシアター**に変貌させます。

### Web版との違い

| 項目 | Web版 (JPHACKS 2025) | Android版 (本システム) |
|------|----------------------|------------------------|
| **通信方式** | WebSocket → Raspberry Pi → MQTT → ESP-12E | **BLE直接通信** → ESP32 |
| **マイコン** | ESP-12E (ESP8266) | **ESP32** |
| **デバイスハブ** | Raspberry Pi 3 Model B (必須) | **不要** |
| **インターネット** | 必要 (Cloud Run経由) | **不要 (完全ローカル)** |
| **同期方式** | 200ms間隔WebSocket同期 | **リアルタイムBLE (50ms先読み)** |
| **対応プラットフォーム** | Webブラウザ (React) | **Android 8.0+** |

---

## 機能

### 📱 Androidアプリ（3画面構成）

| 画面 | 機能 |
|------|------|
| **再生画面** | 動画とタイムラインを同期再生、キャプション表示 |
| **制御画面** | 各エフェクトを手動で制御（テスト・調整用） |
| **設定画面** | BLEデバイスのスキャン・接続管理 |

### 🔧 ESP32デバイス（3台構成）

| デバイス | デバイス名 | 制御対象 |
|----------|-----------|---------|
| **EffectStation** | `4D_ES_XXXX` | ファン、水噴射、ミスト、LED（RGBW） |
| **ActionDrive Motor1** | `4D_AD1_XXXX` | 振動モーター（背中/前方） |
| **ActionDrive Motor2** | `4D_AD2_XXXX` | 振動モーター（お尻/後方） |

---

## システム構成

### 物理構成図

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android端末                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  4D@HOME App                                               │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │  │
│  │  │ PlaybackScreen│ │ControlScreen│ │SettingsScreen│        │  │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │  │
│  │         │                │                │                │  │
│  │  ┌──────┴────────────────┴────────────────┴──────┐        │  │
│  │  │         PlaybackSyncEngine / CommandSender      │        │  │
│  │  └───────────────────────┬───────────────────────┘        │  │
│  │                          │                                 │  │
│  │  ┌───────────────────────┴───────────────────────┐        │  │
│  │  │           BLE Device Manager                    │        │  │
│  │  └───────────────────────┬───────────────────────┘        │  │
│  └──────────────────────────┼────────────────────────────────┘  │
└─────────────────────────────┼────────────────────────────────────┘
                              │ BLE (Bluetooth Low Energy)
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ EffectStation │    │ActionDrive M1 │    │ActionDrive M2 │
│   (4D_ES_*)   │    │  (4D_AD1_*)   │    │  (4D_AD2_*)   │
│               │    │               │    │               │
│ ・ファン      │    │ ・振動モーター│    │ ・振動モーター│
│ ・水噴射      │    │   (背中/前方) │    │   (お尻/後方) │
│ ・ミスト      │    │               │    │               │
│ ・LED (RGBW)  │    │               │    │               │
└───────────────┘    └───────────────┘    └───────────────┘
     ESP32               ESP32               ESP32
```

### ソフトウェアアーキテクチャ

**MVVM + Clean Architecture** を採用

```
app/src/main/java/com/wildcard/fourd_at_home/
├── FourdAtHomeApplication.kt    # Hilt Application
├── MainActivity.kt              # エントリーポイント
│
├── ble/                         # BLE通信レイヤー (Data Layer)
│   ├── BleConstants.kt          # UUID/定数定義
│   ├── BleModels.kt             # データモデル
│   ├── BleScanner.kt            # デバイススキャン
│   ├── BleDeviceManager.kt      # 接続管理
│   └── CommandSender.kt         # コマンド送信
│
├── playback/                    # 再生同期レイヤー (Domain Layer)
│   ├── TimelineModels.kt        # タイムラインモデル
│   ├── TimelineParser.kt        # JSONパーサー
│   └── PlaybackSyncEngine.kt    # 同期エンジン
│
├── data/                        # データ永続化 (Data Layer)
│   └── SettingsRepository.kt    # DataStore設定
│
├── domain/                      # ドメインモデル
│   └── Content.kt               # コンテンツモデル
│
├── di/                          # 依存性注入モジュール (Hilt)
│   ├── BleModule.kt
│   ├── DataModule.kt
│   └── PlaybackModule.kt
│
└── ui/                          # Presentation Layer (Jetpack Compose)
    ├── navigation/              # Navigation Rail
    ├── playback/                # 再生画面
    ├── control/                 # 制御画面
    ├── settings/                # 設定画面
    └── theme/                   # Material 3テーマ
```

---

## 技術スタック

### Android アプリケーション

| カテゴリ | 技術 | バージョン | 用途 |
|---------|------|-----------|------|
| **言語** | Kotlin | 2.0.21 | メイン開発言語 |
| **UI** | Jetpack Compose | BOM 2024.12.01 | 宣言的UI |
| **デザイン** | Material Design 3 | - | テーマ・コンポーネント |
| **ナビゲーション** | Navigation Compose | 2.8.5 | 画面遷移 |
| **DI** | Hilt | 2.53.1 | 依存性注入 |
| **動画再生** | Media3 ExoPlayer | 1.5.1 | 動画プレイヤー |
| **非同期処理** | Kotlin Coroutines | 1.9.0 | 非同期処理・Flow |
| **永続化** | DataStore Preferences | 1.1.2 | 設定保存 |
| **シリアライズ** | kotlinx.serialization | 1.7.3 | JSONパース |

### ESP32 ファームウェア

| カテゴリ | 技術 | 用途 |
|---------|------|------|
| **フレームワーク** | Arduino | ESP32プログラミング |
| **ビルドシステム** | PlatformIO | ビルド・書き込み |
| **BLEライブラリ** | ESP32 BLE Arduino | BLE通信 |
| **LEDライブラリ** | Adafruit NeoPixel | RGBW LED制御 |

### システム要件

#### Android端末
- **OS**: Android 8.0 (API 26) 以上
- **Bluetooth**: BLE (Bluetooth Low Energy) 対応必須
- **画面**: 横向き固定推奨

#### ESP32
- **ボード**: ESP32 DevKit または互換ボード
- **フラッシュ**: 4MB以上
- **電源**: 3.3V / 5V

---

## 対応エフェクト

| エフェクト | デバイス | 制御方式 | モード |
|-----------|----------|----------|--------|
| **振動** | ActionDrive Motor1/2 | 4段階強度 + パターン | 上/下/上下 × 弱/中弱/中強/強, 心拍 |
| **フラッシュ** | EffectStation | PWM調光 | 点灯, 点滅, 呼吸 |
| **カラーLED** | EffectStation | RGBW NeoPixel | 12色（ピンク, 赤, オレンジ, 黄, 黄緑, 緑, 深緑, シアン, 青, 紫, 白, 消灯） |
| **水噴射** | EffectStation | ワンショット | burst |
| **ファン（風）** | EffectStation | ON/OFF | burst |
| **ミスト** | EffectStation | トグル | burst |

---

## タイムラインJSON形式

### 基本構造

```json
{
  "events": [
    {
      "t": 0.0,
      "action": "caption",
      "text": "シーンの説明文"
    },
    {
      "t": 0.0,
      "action": "start",
      "effect": "vibration",
      "mode": "down_weak"
    },
    {
      "t": 5.0,
      "action": "stop",
      "effect": "vibration",
      "mode": "down_weak"
    },
    {
      "t": 3.0,
      "action": "shot",
      "effect": "water",
      "mode": "burst"
    }
  ]
}
```

### イベントタイプ

| action | 説明 | 必須フィールド |
|--------|------|---------------|
| `caption` | キャプション表示 | `t`, `text` |
| `start` | エフェクト開始 | `t`, `effect`, `mode` |
| `stop` | エフェクト停止 | `t`, `effect`, `mode` |
| `shot` | ワンショット発火 | `t`, `effect`, `mode` |

### エフェクトタイプ

| effect | 対象デバイス | 説明 |
|--------|-------------|------|
| `vibration` | ActionDrive | 振動（16パターン） |
| `flash` | EffectStation | フラッシュ（白色LED） |
| `color` | EffectStation | カラーLED（12色） |
| `water` | EffectStation | 水噴射 |
| `wind` | EffectStation | ファン（風） |
| `mist` | EffectStation | ミスト |

---

## BLEプロトコル

### UUIDs

| 種別 | UUID |
|------|------|
| Service | `4D580001-0000-1000-8000-00805F9B34FB` |
| Command Characteristic | `4D580002-0000-1000-8000-00805F9B34FB` |
| Status Characteristic | `4D580003-0000-1000-8000-00805F9B34FB` |

### デバイス名パターン

| デバイス | パターン | 例 |
|----------|---------|-----|
| EffectStation | `4D_ES_XXXX` | `4D_ES_A1B2` |
| ActionDrive Motor1 | `4D_AD1_XXXX` | `4D_AD1_C3D4` |
| ActionDrive Motor2 | `4D_AD2_XXXX` | `4D_AD2_E5F6` |

### 通信特性

| 項目 | 値 |
|------|-----|
| **BLEバージョン** | 4.2以上 |
| **通信距離** | 約10m（屋内環境） |
| **接続数** | 最大3台同時 |
| **書き込み方式** | Write Request (応答あり) |
| **通知方式** | Notification (CCCD有効化) |

---

## ビルド手順

### Androidアプリ

```bash
# 必要な環境
# - Android Studio Ladybug (2024.2.x) 以上
# - JDK 11以上
# - Android SDK 35

# プロジェクトのビルド
./gradlew assembleDebug
```

### ESP32ファームウェア

```bash
# PlatformIO CLI インストール
pip install platformio

# EffectStation ビルド＆書き込み
cd esp32/effect_station
pio run --target upload

# ActionDrive Motor1 ビルド＆書き込み
cd ../action_drive_motor1
pio run --target upload

# ActionDrive Motor2 ビルド＆書き込み
cd ../action_drive_motor2
pio run --target upload

# シリアルモニター
pio device monitor --baud 115200
```

---

## 使用方法

### 1. ESP32セットアップ
1. 各ESP32にファームウェアを書き込み
2. 電源を入れるとBLEアドバタイジング開始

### 2. Androidアプリ接続
1. アプリを起動
2. **設定画面**でBLEデバイスをスキャン
3. 各デバイス（ES, M1, M2）を接続

### 3. 再生
1. **再生画面**で動画とタイムラインJSONを読み込み
2. 再生ボタンで同期再生開始

### 4. 手動制御
- **制御画面**で各エフェクトを個別に調整・テスト可能

---

## ドキュメント

詳細な技術仕様については、以下の仕様書を参照してください：

| No. | ファイル | 内容 |
|-----|---------|------|
| 00 | [SYSTEM_OVERVIEW.md](docs/specifications/00_SYSTEM_OVERVIEW.md) | システム概要 |
| 01 | [ANDROID_ARCHITECTURE.md](docs/specifications/01_ANDROID_ARCHITECTURE.md) | Androidアプリ アーキテクチャ仕様 |
| 02 | [BLE_COMMUNICATION.md](docs/specifications/02_BLE_COMMUNICATION.md) | BLE通信プロトコル仕様 |
| 03 | [TIMELINE_FORMAT.md](docs/specifications/03_TIMELINE_FORMAT.md) | タイムラインJSON仕様 |
| 04 | [ESP32_EFFECT_STATION.md](docs/specifications/04_ESP32_EFFECT_STATION.md) | EffectStation ファームウェア仕様 |
| 05 | [ESP32_ACTION_DRIVE.md](docs/specifications/05_ESP32_ACTION_DRIVE.md) | ActionDrive ファームウェア仕様 |
| 06 | [SYSTEM_INTEGRATION.md](docs/specifications/06_SYSTEM_INTEGRATION.md) | システム統合仕様 |
| 07 | [UI_COMPONENTS.md](docs/specifications/07_UI_COMPONENTS.md) | UIコンポーネント仕様 |
| 08 | [SETTINGS_DATA.md](docs/specifications/08_SETTINGS_DATA.md) | 設定・データ永続化仕様 |

---

## 今後の展望

### もっと賢く、もっと便利に
- **AIの進化**: シーンの理解精度を高め、より「ちょうどいい」タイミングで効果を発動
- **AI動画解析統合**: Gemini 2.5 Proによる自動タイムライン生成機能の統合
- **あなた好みに調整**: 効果の強さや種類を自分好みにカスタマイズ

### もっと速く、もっと正確に
- **同期精度の向上**: 映像と効果のズレをさらに小さく、違和感のない体験へ
- **BLE通信最適化**: より高速なコマンド送信と応答処理

### 音と連動する新体験
- **音楽に合わせて振動**: ライブ映像やMVで、ビートに合わせた振動体験
- **効果音で臨場感UP**: 雷鳴で光り、銃声で振動、風の音で風が吹く

### 拡張機能
- **iOS版開発**: iOSアプリでの対応
- **マルチデバイス対応**: 複数Android端末からの同時制御
- **タイムラインエディタ**: アプリ内でのタイムライン編集機能

---

## プロジェクト構成

```
├── app/                          # Androidアプリ
│   └── src/main/java/com/wildcard/fourd_at_home/
│       ├── ble/                  # BLE通信レイヤー
│       ├── playback/             # 再生同期
│       ├── data/                 # データ永続化
│       ├── domain/               # ドメインモデル
│       ├── di/                   # Hilt DI
│       └── ui/                   # Compose UI
├── esp32/                        # ESP32ファームウェア
│   ├── effect_station/           # EffectStation
│   ├── action_drive_motor1/      # ActionDrive Motor1
│   └── action_drive_motor2/      # ActionDrive Motor2
└── docs/                         # ドキュメント
    ├── specifications/           # 詳細仕様書
    └── plan/                     # 開発計画
```

---

## ライセンス

MIT License

## 貢献

プルリクエスト歓迎します！
