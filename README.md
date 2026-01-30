# 4D@HOME Android

家庭で4DX体験を再現するAndroidアプリケーション。
BLE（Bluetooth Low Energy）を使用してESP32デバイスと通信し、動画再生に同期したエフェクトを制御します。

## 機能

### 📱 Androidアプリ
- **再生画面**: 動画とタイムラインを同期再生
- **制御画面**: 各エフェクトを手動で制御
- **設定画面**: BLEデバイスのスキャン・接続管理

### 🎬 対応エフェクト
- **ファン（風）**: PWM制御による風量調整
- **水噴射**: タイミング制御
- **ミスト**: 霧効果
- **LED**: RGB NeoPixelによる照明効果
- **振動**: 2つの振動モーターによる体感効果

### 🔧 ESP32デバイス
- **EffectStation**: ファン、水、ミスト、LEDを制御
- **ActionDrive Motor1**: 振動モーター1（左/前）
- **ActionDrive Motor2**: 振動モーター2（右/後）

## システム要件

### Android
- Android 8.0 (API 26) 以上
- Bluetooth Low Energy対応デバイス
- 画面: 横向き固定推奨

### ESP32
- ESP32 DevKit または互換ボード
- PlatformIO でビルド

## プロジェクト構成

```
├── app/                          # Androidアプリ
│   └── src/main/java/.../
│       ├── ble/                  # BLE通信レイヤー
│       │   ├── BleConstants.kt   # UUID定義
│       │   ├── BleModels.kt      # データモデル
│       │   ├── BleScanner.kt     # デバイススキャン
│       │   ├── BleDeviceManager.kt # 接続管理
│       │   └── CommandSender.kt  # コマンド送信
│       ├── playback/             # 再生同期
│       │   ├── TimelineModels.kt # タイムラインモデル
│       │   ├── TimelineParser.kt # JSONパーサー
│       │   └── PlaybackSyncEngine.kt # 同期エンジン
│       ├── data/                 # データ永続化
│       │   └── SettingsRepository.kt # 設定保存
│       ├── di/                   # Hilt DI
│       └── ui/                   # Compose UI
│           ├── playback/         # 再生画面
│           ├── control/          # 制御画面
│           ├── settings/         # 設定画面
│           ├── navigation/       # Navigation Rail
│           └── theme/            # テーマ
├── esp32/                        # ESP32ファームウェア
│   ├── effect_station/           # EffectStation
│   ├── action_drive_motor1/      # ActionDrive Motor1
│   └── action_drive_motor2/      # ActionDrive Motor2
└── docs/                         # ドキュメント
```

## 技術スタック

### Android
- **言語**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **動画再生**: Media3 ExoPlayer
- **永続化**: DataStore Preferences
- **シリアライズ**: kotlinx.serialization

### ESP32
- **フレームワーク**: Arduino
- **ビルドシステム**: PlatformIO
- **BLEライブラリ**: ESP32 BLE
- **LEDライブラリ**: Adafruit NeoPixel

## BLEプロトコル

### UUIDs
| 種別 | UUID |
|------|------|
| Service | `4D580001-0000-1000-8000-00805F9B34FB` |
| Command Characteristic | `4D580002-0000-1000-8000-00805F9B34FB` |
| Status Characteristic | `4D580003-0000-1000-8000-00805F9B34FB` |

### デバイス名パターン
- EffectStation: `4D_ES_XXXX`
- ActionDrive Motor1: `4D_AD1_XXXX`
- ActionDrive Motor2: `4D_AD2_XXXX`

### コマンド形式
```
[コマンドID] [パラメータ1] [パラメータ2] ...

EffectStation:
- 0x01 [強度]         : ファン (0-255)
- 0x02 [強度]         : 水噴射 (0-255)
- 0x03 [強度]         : ミスト (0-255)
- 0x04 [R] [G] [B] [明] : LED制御
- 0xFF                : 全停止

ActionDrive:
- 0x10 [強度]         : 振動 (0-255)
- 0x00                : 停止
```

## タイムラインJSON形式

```json
{
  "version": "1.0",
  "title": "サンプル",
  "duration": 30000,
  "events": [
    {
      "time": 0,
      "type": "led",
      "params": {
        "r": 255,
        "g": 0,
        "b": 0,
        "brightness": 200
      }
    },
    {
      "time": 2000,
      "type": "fan",
      "params": {
        "intensity": 180
      }
    },
    {
      "time": 5000,
      "type": "vibration",
      "params": {
        "intensity": 200,
        "motor": 0
      }
    }
  ]
}
```

### イベントタイプ
- `fan`: ファン
- `water`: 水噴射
- `mist`: ミスト
- `led`: LED
- `vibration`: 振動 (motor: 0=両方, 1=Motor1, 2=Motor2)
- `all_off`: 全停止

## ビルド手順

### Androidアプリ
```bash
# プロジェクトルートで
./gradlew assembleDebug
```

### ESP32ファームウェア
```bash
# PlatformIO CLI使用
cd esp32/effect_station
pio run --target upload

cd ../action_drive_motor1
pio run --target upload

cd ../action_drive_motor2
pio run --target upload
```

## 使用方法

1. **ESP32セットアップ**
   - 各ESP32にファームウェアを書き込み
   - 電源を入れるとBLEアドバタイジング開始

2. **Androidアプリ**
   - アプリを起動
   - 設定画面でBLEデバイスをスキャン
   - 各デバイス（ES, M1, M2）を接続

3. **再生**
   - 再生画面で動画とタイムラインを読み込み
   - 再生ボタンで同期再生開始

4. **手動制御**
   - 制御画面で各エフェクトを個別に調整可能

## ライセンス

MIT License

## 貢献

プルリクエスト歓迎します！
