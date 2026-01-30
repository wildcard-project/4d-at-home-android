# 4D@HOME Android 詳細仕様書 - ESP32 ActionDrive ファームウェア仕様

**バージョン**: 1.0.0  
**作成日**: 2025年1月30日  
**対象**: ActionDrive Motor 1/2 (4D_AD1_XXXX / 4D_AD2_XXXX)

---

## 📑 目次

1. [概要](#1-概要)
2. [ハードウェア構成](#2-ハードウェア構成)
3. [ピン定義](#3-ピン定義)
4. [BLE設定](#4-ble設定)
5. [コマンド処理](#5-コマンド処理)
6. [振動モード](#6-振動モード)
7. [パターン再生](#7-パターン再生)
8. [ステータス通知](#8-ステータス通知)
9. [ビルド設定](#9-ビルド設定)
10. [Motor1/Motor2の違い](#10-motor1motor2の違い)

---

## 1. 概要

### 1.1 ActionDriveとは

ActionDriveは、振動モーターを制御するESP32ベースのデバイスです。4ピン構成のモーターを独立制御し、多彩な振動パターンを実現します。

### 1.2 システム構成

| 名称 | デバイス名パターン | 用途 |
|------|-------------------|------|
| ActionDrive Motor 1 | 4D_AD1_XXXX | メイン振動モーター |
| ActionDrive Motor 2 | 4D_AD2_XXXX | サブ振動モーター |

### 1.3 振動強度

| 強度 | 名称 | ピン出力パターン |
|------|------|-----------------|
| STRONG | 強 | 4ピン全ON |
| MEDIUM_STRONG | 中強 | 3ピンON + 1ピンPWM |
| MEDIUM_WEAK | 中弱 | 2ピンON + 2ピンOFF |
| WEAK | 弱 | 1ピンON + 3ピンOFF |
| OFF | 停止 | 全ピンOFF |

---

## 2. ハードウェア構成

### 2.1 使用マイコン

| 項目 | 仕様 |
|------|------|
| **マイコン** | ESP32 DevKit |
| **CPU** | Xtensa LX6 デュアルコア 240MHz |
| **RAM** | 520KB SRAM |
| **Flash** | 4MB |
| **Bluetooth** | BLE 4.2 |

### 2.2 モーター接続

```
ESP32 DevKit
├── GPIO 25 ─── MOSFETドライバ ─── モーターピン1
├── GPIO 26 ─── MOSFETドライバ ─── モーターピン2
├── GPIO 27 ─── MOSFETドライバ ─── モーターピン3
└── GPIO 32 ─── MOSFETドライバ ─── モーターピン4
```

### 2.3 回路構成

```
                    ┌───────────────────┐
                    │     ESP32         │
                    │                   │
        ┌───────────┤ GPIO25 (PIN_1)    │
        │           │                   │
        │   ┌───────┤ GPIO26 (PIN_2)    │
        │   │       │                   │
        │   │   ┌───┤ GPIO27 (PIN_3)    │
        │   │   │   │                   │
        │   │   │ ┌─┤ GPIO32 (PIN_4)    │
        │   │   │ │ │                   │
        │   │   │ │ │          GND ────┬┘
        │   │   │ │ │                  │
        ▼   ▼   ▼ ▼                    ▼
      ┌───┬───┬───┬───┐               GND
      │ M │ M │ M │ M │
      │ 1 │ 2 │ 3 │ 4 │  4ピンモーター
      └───┴───┴───┴───┘
           │
           ▼
        振動出力
```

### 2.4 PWM設定

| 項目 | 値 |
|------|-----|
| PWMチャンネル | 4 (PIN_4用) |
| PWM周波数 | 1000 Hz |
| PWM解像度 | 8ビット (0-255) |
| PWM値 (MEDIUM_STRONG) | 150/255 |

---

## 3. ピン定義

### 3.1 ピンアサイン

```cpp
// ピン定義
#define PIN_1    25   // モーターピン1
#define PIN_2    26   // モーターピン2
#define PIN_3    27   // モーターピン3
#define PIN_4    32   // モーターピン4 (PWM対応)
```

### 3.2 PWM設定

```cpp
// PWM設定
#define PWM_CHANNEL  4
#define PWM_FREQ     1000
#define PWM_RES      8
```

---

## 4. BLE設定

### 4.1 UUID定義

EffectStationと共通のUUIDを使用します。

```cpp
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"
```

### 4.2 デバイス名生成

```cpp
// action_drive_motor1/main.cpp
#define DEVICE_PREFIX "4D_AD1_"

// action_drive_motor2/main.cpp  
#define DEVICE_PREFIX "4D_AD2_"

String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    sprintf(name, "%s%02X%02X", DEVICE_PREFIX, mac[4], mac[5]);
    return String(name);
}
```

### 4.3 BLE初期化

```cpp
void setup() {
    Serial.begin(115200);
    
    // ピン初期化
    pinMode(PIN_1, OUTPUT);
    pinMode(PIN_2, OUTPUT);
    pinMode(PIN_3, OUTPUT);
    pinMode(PIN_4, OUTPUT);
    
    // PWM設定
    ledcSetup(PWM_CHANNEL, PWM_FREQ, PWM_RES);
    ledcAttachPin(PIN_4, PWM_CHANNEL);
    
    // 初期状態: OFF
    allPinsOff();
    
    // BLE初期化
    String deviceName = getDeviceName();
    BLEDevice::init(deviceName.c_str());
    
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());
    
    BLEService* pService = pServer->createService(SERVICE_UUID);
    
    pCommandChar = pService->createCharacteristic(
        COMMAND_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pCommandChar->setCallbacks(new CommandCallbacks());
    
    pStatusChar = pService->createCharacteristic(
        STATUS_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusChar->addDescriptor(new BLE2902());
    
    pService->start();
    
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->start();
}
```

---

## 5. コマンド処理

### 5.1 コマンドコールバック

```cpp
class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) {
        std::string stdValue = pCharacteristic->getValue();
        String value = String(stdValue.c_str());
        if (value.length() > 0) {
            processStringCommand(value);
        }
    }
};
```

### 5.2 コマンドパース

```cpp
void processStringCommand(const String& cmd) {
    int firstComma = cmd.indexOf(',');
    String cmdType = (firstComma > 0) ? cmd.substring(0, firstComma) : cmd;
    cmdType.trim();
    cmdType.toUpperCase();
    
    if (cmdType == "MOTOR") {
        String modeStr = cmd.substring(firstComma + 1);
        modeStr.trim();
        modeStr.toUpperCase();
        
        if (modeStr == "STRONG") {
            setMotorStrong();
        }
        else if (modeStr == "MEDIUM_STRONG") {
            setMotorMediumStrong();
        }
        else if (modeStr == "MEDIUM_WEAK") {
            setMotorMediumWeak();
        }
        else if (modeStr == "WEAK") {
            setMotorWeak();
        }
        else if (modeStr == "OFF" || modeStr == "0") {
            setMotorOff();
        }
        else {
            // パターンコマンド
            if (modeStr == "PATTERN_RUMBLE") {
                startPattern(PATTERN_RUMBLE);
            }
            else if (modeStr == "PATTERN_PULSE") {
                startPattern(PATTERN_PULSE);
            }
            else if (modeStr == "PATTERN_WAVE") {
                startPattern(PATTERN_WAVE);
            }
        }
    }
    else if (cmdType == "OFF" || cmdType == "ALL_OFF") {
        setMotorOff();
    }
    
    sendStatus();
}
```

### 5.3 コマンド一覧

| コマンド | 形式 | 説明 |
|---------|------|------|
| MOTOR,STRONG | `MOTOR,STRONG` | 最大振動 |
| MOTOR,MEDIUM_STRONG | `MOTOR,MEDIUM_STRONG` | 中強振動 |
| MOTOR,MEDIUM_WEAK | `MOTOR,MEDIUM_WEAK` | 中弱振動 |
| MOTOR,WEAK | `MOTOR,WEAK` | 弱振動 |
| MOTOR,OFF | `MOTOR,OFF` | 停止 |
| MOTOR,PATTERN_RUMBLE | `MOTOR,PATTERN_RUMBLE` | 地鳴りパターン |
| MOTOR,PATTERN_PULSE | `MOTOR,PATTERN_PULSE` | パルスパターン |
| MOTOR,PATTERN_WAVE | `MOTOR,PATTERN_WAVE` | 波パターン |
| OFF | `OFF` | 全停止 |

---

## 6. 振動モード

### 6.1 モード定義

```cpp
enum MotorMode {
    MODE_OFF = 0,
    MODE_WEAK = 1,
    MODE_MEDIUM_WEAK = 2,
    MODE_MEDIUM_STRONG = 3,
    MODE_STRONG = 4
};

MotorMode currentMode = MODE_OFF;
```

### 6.2 STRONG (強)

```cpp
void setMotorStrong() {
    currentMode = MODE_STRONG;
    stopPattern();
    
    // 全ピンON
    digitalWrite(PIN_1, HIGH);
    digitalWrite(PIN_2, HIGH);
    digitalWrite(PIN_3, HIGH);
    digitalWrite(PIN_4, HIGH);
    
    Serial.println("Motor: STRONG");
}
```

### 6.3 MEDIUM_STRONG (中強)

```cpp
void setMotorMediumStrong() {
    currentMode = MODE_MEDIUM_STRONG;
    stopPattern();
    
    // 3ピンON + 1ピンPWM
    digitalWrite(PIN_1, HIGH);
    digitalWrite(PIN_2, HIGH);
    digitalWrite(PIN_3, HIGH);
    ledcWrite(PWM_CHANNEL, 150);  // 約60%
    
    Serial.println("Motor: MEDIUM_STRONG");
}
```

### 6.4 MEDIUM_WEAK (中弱)

```cpp
void setMotorMediumWeak() {
    currentMode = MODE_MEDIUM_WEAK;
    stopPattern();
    
    // 2ピンON + 2ピンOFF
    digitalWrite(PIN_1, HIGH);
    digitalWrite(PIN_2, HIGH);
    digitalWrite(PIN_3, LOW);
    digitalWrite(PIN_4, LOW);
    
    Serial.println("Motor: MEDIUM_WEAK");
}
```

### 6.5 WEAK (弱)

```cpp
void setMotorWeak() {
    currentMode = MODE_WEAK;
    stopPattern();
    
    // 1ピンON + 3ピンOFF
    digitalWrite(PIN_1, HIGH);
    digitalWrite(PIN_2, LOW);
    digitalWrite(PIN_3, LOW);
    digitalWrite(PIN_4, LOW);
    
    Serial.println("Motor: WEAK");
}
```

### 6.6 OFF (停止)

```cpp
void setMotorOff() {
    currentMode = MODE_OFF;
    stopPattern();
    allPinsOff();
    
    Serial.println("Motor: OFF");
}

void allPinsOff() {
    digitalWrite(PIN_1, LOW);
    digitalWrite(PIN_2, LOW);
    digitalWrite(PIN_3, LOW);
    digitalWrite(PIN_4, LOW);
    ledcWrite(PWM_CHANNEL, 0);
}
```

---

## 7. パターン再生

### 7.1 パターン定義

```cpp
enum PatternType {
    PATTERN_NONE = 0,
    PATTERN_RUMBLE = 1,   // 地鳴り (ランダム振動)
    PATTERN_PULSE = 2,    // パルス (周期的ON/OFF)
    PATTERN_WAVE = 3      // 波 (強弱変化)
};

PatternType currentPattern = PATTERN_NONE;
unsigned long patternStartTime = 0;
int patternStep = 0;
```

### 7.2 パターン開始/停止

```cpp
void startPattern(PatternType pattern) {
    currentPattern = pattern;
    patternStartTime = millis();
    patternStep = 0;
    Serial.printf("Pattern started: %d\n", pattern);
}

void stopPattern() {
    currentPattern = PATTERN_NONE;
    patternStep = 0;
}
```

### 7.3 パターン更新

```cpp
void updatePattern() {
    if (currentPattern == PATTERN_NONE) return;
    
    unsigned long elapsed = millis() - patternStartTime;
    
    switch (currentPattern) {
        case PATTERN_RUMBLE:
            updateRumble(elapsed);
            break;
        case PATTERN_PULSE:
            updatePulse(elapsed);
            break;
        case PATTERN_WAVE:
            updateWave(elapsed);
            break;
        default:
            break;
    }
}
```

### 7.4 地鳴りパターン (RUMBLE)

```cpp
void updateRumble(unsigned long elapsed) {
    // 50ms間隔でランダムな強度を設定
    int step = elapsed / 50;
    if (step != patternStep) {
        patternStep = step;
        
        // ランダムに1-4ピンをON
        int numPins = random(1, 5);
        allPinsOff();
        
        for (int i = 0; i < numPins; i++) {
            int pin = random(0, 4);
            switch (pin) {
                case 0: digitalWrite(PIN_1, HIGH); break;
                case 1: digitalWrite(PIN_2, HIGH); break;
                case 2: digitalWrite(PIN_3, HIGH); break;
                case 3: digitalWrite(PIN_4, HIGH); break;
            }
        }
    }
}
```

### 7.5 パルスパターン (PULSE)

```cpp
void updatePulse(unsigned long elapsed) {
    // 200msでON/OFFを切り替え
    bool on = ((elapsed / 200) % 2) == 0;
    
    if (on) {
        digitalWrite(PIN_1, HIGH);
        digitalWrite(PIN_2, HIGH);
        digitalWrite(PIN_3, HIGH);
        digitalWrite(PIN_4, HIGH);
    } else {
        allPinsOff();
    }
}
```

### 7.6 波パターン (WAVE)

```cpp
void updateWave(unsigned long elapsed) {
    // サイン波で強度変化 (周期2秒)
    float phase = (elapsed % 2000) / 2000.0f * 2 * PI;
    float intensity = (sin(phase) + 1.0f) / 2.0f;
    
    // PWMで強度を表現
    int pwmValue = (int)(intensity * 255);
    
    // 全ピンをPWM制御 (PIN_4のみ真のPWM、他はdigital)
    digitalWrite(PIN_1, intensity > 0.25 ? HIGH : LOW);
    digitalWrite(PIN_2, intensity > 0.5 ? HIGH : LOW);
    digitalWrite(PIN_3, intensity > 0.75 ? HIGH : LOW);
    ledcWrite(PWM_CHANNEL, pwmValue);
}
```

---

## 8. ステータス通知

### 8.1 ステータス送信

```cpp
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[4] = {
            (uint8_t)currentMode,
            (uint8_t)currentPattern,
            0x00,
            0x00
        };
        pStatusChar->setValue(status, 4);
        pStatusChar->notify();
    }
}
```

### 8.2 ステータスバイト形式

| バイト | 内容 | 値 |
|--------|------|-----|
| [0] | モード | 0=OFF, 1=WEAK, 2=MEDIUM_WEAK, 3=MEDIUM_STRONG, 4=STRONG |
| [1] | パターン | 0=なし, 1=RUMBLE, 2=PULSE, 3=WAVE |
| [2] | 予約 | 0x00 |
| [3] | 予約 | 0x00 |

---

## 9. ビルド設定

### 9.1 platformio.ini

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200

; lib_deps は現在空 (BLEは標準ライブラリ)
```

### 9.2 ディレクトリ構造

```
esp32/
├── action_drive_motor1/
│   ├── platformio.ini
│   └── src/
│       └── main.cpp
└── action_drive_motor2/
    ├── platformio.ini
    └── src/
        └── main.cpp
```

### 9.3 ビルドコマンド

```bash
# Motor1をビルド
cd esp32/action_drive_motor1
pio run

# Motor2をビルド
cd esp32/action_drive_motor2
pio run

# 書き込み
pio run --target upload

# シリアルモニター
pio device monitor --baud 115200
```

---

## 10. Motor1/Motor2の違い

### 10.1 コードの違い

Motor1とMotor2のソースコードは**ほぼ同一**です。唯一の違いは**デバイス名のプレフィックス**のみです。

| 項目 | Motor1 | Motor2 |
|------|--------|--------|
| ファイル | action_drive_motor1/src/main.cpp | action_drive_motor2/src/main.cpp |
| プレフィックス | `4D_AD1_` | `4D_AD2_` |
| 例 | `4D_AD1_A1B2` | `4D_AD2_C3D4` |

### 10.2 なぜ2つあるか

映画館の4DX/MX4Dシステムでは、複数の振動源を独立制御することで、より複雑な体感を実現します。

| 構成 | 用途例 |
|------|--------|
| 1台構成 | シンプルな振動 |
| 2台構成 | 左右独立振動、前後振動 |

### 10.3 Androidアプリでの識別

```kotlin
// BleConstants.kt
const val DEVICE_PREFIX_ACTION_DRIVE_1 = "4D_AD1_"
const val DEVICE_PREFIX_ACTION_DRIVE_2 = "4D_AD2_"

// DeviceType.kt
enum class DeviceType {
    EFFECT_STATION,
    ACTION_DRIVE_1,
    ACTION_DRIVE_2
}
```

### 10.4 タイムラインでの指定

```json
{
    "actions": [
        {
            "action": "VIBRATION",
            "target": "MOTOR1",
            "mode": "STRONG"
        },
        {
            "action": "VIBRATION",
            "target": "MOTOR2",
            "mode": "WEAK"
        }
    ]
}
```

---

## 付録: 完全なモード対応表

### Android → ESP32 コマンドマッピング

| VibrationMode (Kotlin) | esp32Command | 説明 |
|------------------------|--------------|------|
| OFF | MOTOR,OFF | 停止 |
| WEAK | MOTOR,WEAK | 弱振動 |
| MEDIUM_WEAK | MOTOR,MEDIUM_WEAK | 中弱振動 |
| MEDIUM_STRONG | MOTOR,MEDIUM_STRONG | 中強振動 |
| STRONG | MOTOR,STRONG | 強振動 |
| PATTERN_RUMBLE | MOTOR,PATTERN_RUMBLE | 地鳴り |
| PATTERN_PULSE | MOTOR,PATTERN_PULSE | パルス |
| PATTERN_WAVE | MOTOR,PATTERN_WAVE | 波 |

### モードごとの電力消費 (目安)

| モード | ピン出力 | 相対消費電力 |
|--------|----------|-------------|
| OFF | 0/4 | 0% |
| WEAK | 1/4 | 25% |
| MEDIUM_WEAK | 2/4 | 50% |
| MEDIUM_STRONG | 3/4 + PWM | 75% |
| STRONG | 4/4 | 100% |
