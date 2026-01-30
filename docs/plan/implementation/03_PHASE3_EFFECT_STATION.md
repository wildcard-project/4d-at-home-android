# Phase 3: EffectStation実装

**期間目安**: 3-4日  
**前提条件**: Phase 2 完了  
**優先度**: 最高（最初に動かしたいデバイス）

---

## 📋 タスク一覧

| # | タスク | 優先度 | 完了条件 |
|:-:|:-------|:------:|:---------|
| 3.1 | ESP32 BLEファームウェア | 必須 | BLE接続・コマンド受信動作 |
| 3.2 | EffectStationコマンド定義 | 必須 | FAN/SPLASH/MIST/LED対応 |
| 3.3 | Android制御ロジック | 必須 | コマンド送信動作 |
| 3.4 | 制御画面UI | 必須 | 手動エフェクト制御UI |
| 3.5 | 動作テスト | 必須 | 全エフェクト動作確認 |

---

## 3.1 ESP32 BLEファームウェア

### 3.1.1 ディレクトリ構造

```
esp32_firmware/
└── effect_station/
    ├── platformio.ini
    └── src/
        └── main.cpp
```

### 3.1.2 platformio.ini

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino

; シリアルモニタ設定
monitor_speed = 115200

; アップロード設定
upload_speed = 921600

; ビルドフラグ
build_flags = 
    -D LED_BUILTIN=2
    -D DEVICE_TYPE_EFFECT_STATION

; ライブラリ依存
lib_deps =
    adafruit/Adafruit NeoPixel@^1.12.0
```

### 3.1.3 main.cpp (EffectStation BLE版)

```cpp
/**
 * 4DX@HOME EffectStation - BLE Version
 * 
 * ESP32 BLE Server for EffectStation Control
 * 
 * Device Name: 4D_ES_XXXX (XXXX = MAC下4桁)
 * 
 * エフェクト:
 * - FAN (風): GPIO 25
 * - SPLASH (水しぶき): GPIO 26
 * - MIST (ミスト): GPIO 32
 * - LED (RGBW NeoPixel): GPIO 27
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Adafruit_NeoPixel.h>

// ==========================================
// 1. BLE定義
// ==========================================
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// ==========================================
// 2. ピン定義
// ==========================================
const int PIN_FAN    = 25;  // 風 (ON/OFF)
const int PIN_SPLASH = 26;  // 水しぶき (一瞬)
const int PIN_LED    = 27;  // LED (RGBW NeoPixel)
const int PIN_MIST   = 32;  // ミスト (NPNトランジスタ経由)

const int NUM_LEDS   = 1;   // LEDの数

// ==========================================
// 3. グローバル変数
// ==========================================

// BLE
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandCharacteristic = nullptr;
BLECharacteristic* pStatusCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// LED (NeoPixel)
Adafruit_NeoPixel strip(NUM_LEDS, PIN_LED, NEO_RGBW + NEO_KHZ800);
uint32_t colors[12];

// 水しぶき (Splash)
unsigned long splashStartTime = 0;
bool splashActive = false;
const int SPLASH_DURATION = 200; // 水が出る時間(ms)

// ミスト (Mist)
bool isMistOn = false;
int mistAutoOffMode = 0;    // 0:なし, 1:自動OFF待ち
unsigned long mistStartTime = 0;
const int MIST_DURATION = 100; // 一瞬モードの時間(ms)

// LED状態
int ledTargetColorIdx = 11; // 目標の色ID
int ledBrightnessMode = 0;  // 0:なし, 1:弱, 2:強
int ledEffect = 0;          // 0:点灯, 1:点滅, 2:呼吸
int ledTransition = 0;      // 0:一瞬, 1:フェード

unsigned long lastLedUpdate = 0;
float currentR = 0, currentG = 0, currentB = 0, currentW = 0;

// ==========================================
// 4. 関数プロトタイプ
// ==========================================
void setupColors();
void parseCommand(String input);
void updateSplash();
void updateMist();
void updateLED();
void clickMistButton();
void sendStatus(String status);
String getDeviceName();

// ==========================================
// 5. BLEコールバッククラス
// ==========================================

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        deviceConnected = true;
        Serial.println("[BLE] Client connected");
        sendStatus("CONNECTED");
    }

    void onDisconnect(BLEServer* pServer) override {
        deviceConnected = false;
        Serial.println("[BLE] Client disconnected");
    }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        String value = pCharacteristic->getValue().c_str();
        value.trim();
        
        if (value.length() > 0) {
            Serial.print("[BLE] Received command: ");
            Serial.println(value);
            parseCommand(value);
        }
    }
};

// ==========================================
// 6. セットアップ
// ==========================================
void setup() {
    Serial.begin(115200);
    Serial.println("\n===================================");
    Serial.println(" 4DX@HOME EffectStation (BLE)");
    Serial.println("===================================");

    // ピン設定
    pinMode(PIN_FAN, OUTPUT);
    pinMode(PIN_SPLASH, OUTPUT);
    pinMode(PIN_MIST, OUTPUT);
    
    digitalWrite(PIN_FAN, LOW);
    digitalWrite(PIN_SPLASH, LOW);
    digitalWrite(PIN_MIST, LOW);

    // LED初期化
    strip.begin();
    strip.show();
    setupColors();

    // BLE初期化
    String deviceName = getDeviceName();
    Serial.print("Device name: ");
    Serial.println(deviceName);

    BLEDevice::init(deviceName.c_str());
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    // サービス作成
    BLEService* pService = pServer->createService(SERVICE_UUID);

    // Command Characteristic
    pCommandCharacteristic = pService->createCharacteristic(
        COMMAND_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_WRITE_NR
    );
    pCommandCharacteristic->setCallbacks(new CommandCallbacks());

    // Status Characteristic
    pStatusCharacteristic = pService->createCharacteristic(
        STATUS_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusCharacteristic->addDescriptor(new BLE2902());

    // サービス開始
    pService->start();

    // アドバタイジング開始
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println("BLE advertising started");
    Serial.println("===================================\n");
}

// ==========================================
// 7. メインループ
// ==========================================
void loop() {
    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        BLEDevice::startAdvertising();
        Serial.println("[BLE] Restart advertising");
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    // 各機能の自動処理
    updateSplash();
    updateMist();
    updateLED();
}

// ==========================================
// 8. ステータス送信
// ==========================================
void sendStatus(String status) {
    if (deviceConnected && pStatusCharacteristic != nullptr) {
        pStatusCharacteristic->setValue(status.c_str());
        pStatusCharacteristic->notify();
        Serial.print("[BLE] Status: ");
        Serial.println(status);
    }
}

// ==========================================
// 9. デバイス名生成
// ==========================================
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[20];
    sprintf(name, "4D_ES_%02X%02X", mac[4], mac[5]);
    return String(name);
}

// ==========================================
// 10. コマンド解析
// ==========================================
void parseCommand(String input) {
    int firstComma = input.indexOf(',');
    String cmd = (firstComma > 0) ? input.substring(0, firstComma) : input;
    String params = (firstComma > 0) ? input.substring(firstComma + 1) : "";
    cmd.toUpperCase();

    // --- FAN (風) ---
    if (cmd == "FAN") {
        int val = params.toInt();
        if (val == 1) {
            digitalWrite(PIN_FAN, HIGH);
            Serial.println("CMD: Fan ON");
            sendStatus("FAN:ON");
        } else {
            digitalWrite(PIN_FAN, LOW);
            Serial.println("CMD: Fan OFF");
            sendStatus("FAN:OFF");
        }
    }
    
    // --- SPLASH (水) ---
    else if (cmd == "SPLASH") {
        splashActive = true;
        splashStartTime = millis();
        digitalWrite(PIN_SPLASH, HIGH);
        Serial.println("CMD: Splash Fired");
        sendStatus("SPLASH:FIRED");
    }
    
    // --- MIST (ミスト) ---
    else if (cmd == "MIST") {
        int reqMode = params.toInt();
        
        if (reqMode == 1) { // 一瞬モード
            if (!isMistOn) {
                clickMistButton();
                isMistOn = true;
            }
            mistAutoOffMode = 1;
            mistStartTime = millis();
            Serial.println("CMD: Mist 1-Shot");
            sendStatus("MIST:SHOT");
        }
        else if (reqMode == 2) { // 継続モード
            mistAutoOffMode = 0;
            if (!isMistOn) {
                clickMistButton();
                isMistOn = true;
                Serial.println("CMD: Mist ON");
                sendStatus("MIST:ON");
            }
        }
        else { // OFFモード
            mistAutoOffMode = 0;
            if (isMistOn) {
                clickMistButton();
                isMistOn = false;
                Serial.println("CMD: Mist OFF");
                sendStatus("MIST:OFF");
            }
        }
    }
    
    // --- LED ---
    else if (cmd == "LED") {
        int p[4] = {11, 0, 0, 0}; // デフォルト値
        int strIdx = 0;
        
        for(int i = 0; i < 4; i++) {
            int nextComma = params.indexOf(',', strIdx);
            if(nextComma == -1) nextComma = params.length();
            if (strIdx < params.length()) {
                p[i] = params.substring(strIdx, nextComma).toInt();
            }
            strIdx = nextComma + 1;
        }
        
        ledTargetColorIdx = constrain(p[0], 0, 11);
        ledBrightnessMode = constrain(p[1], 0, 2);
        ledEffect = constrain(p[2], 0, 2);
        ledTransition = constrain(p[3], 0, 1);

        if (ledTransition == 0) {
            uint32_t c = colors[ledTargetColorIdx];
            if (ledBrightnessMode == 0) c = colors[11];
            
            currentR = (uint8_t)(c >> 16);
            currentG = (uint8_t)(c >> 8);
            currentB = (uint8_t)(c);
            currentW = (uint8_t)(c >> 24);
        }
        
        Serial.printf("CMD: LED Color=%d Bright=%d Effect=%d Trans=%d\n",
                      ledTargetColorIdx, ledBrightnessMode, ledEffect, ledTransition);
        
        char statusMsg[32];
        sprintf(statusMsg, "LED:%d,%d,%d,%d", 
                ledTargetColorIdx, ledBrightnessMode, ledEffect, ledTransition);
        sendStatus(statusMsg);
    }
    
    // --- 不明なコマンド ---
    else {
        Serial.print("Unknown command: ");
        Serial.println(input);
        sendStatus("ERR:UNKNOWN");
    }
}

// ==========================================
// 11. ミストボタン制御
// ==========================================
void clickMistButton() {
    digitalWrite(PIN_MIST, HIGH);
    delay(100);
    digitalWrite(PIN_MIST, LOW);
    delay(100);
}

// ==========================================
// 12. 自動更新ロジック
// ==========================================

void updateSplash() {
    if (splashActive) {
        if (millis() - splashStartTime >= SPLASH_DURATION) {
            digitalWrite(PIN_SPLASH, LOW);
            splashActive = false;
        }
    }
}

void updateMist() {
    if (mistAutoOffMode == 1) {
        if (millis() - mistStartTime >= MIST_DURATION) {
            if (isMistOn) {
                clickMistButton();
                isMistOn = false;
                Serial.println("AUTO: Mist Timer OFF");
            }
            mistAutoOffMode = 0;
        }
    }
}

void updateLED() {
    unsigned long now = millis();
    if (now - lastLedUpdate < 30) return;
    lastLedUpdate = now;

    uint32_t targetColorRaw = colors[ledTargetColorIdx];
    if (ledBrightnessMode == 0) targetColorRaw = colors[11];

    float tR = (uint8_t)(targetColorRaw >> 16);
    float tG = (uint8_t)(targetColorRaw >> 8);
    float tB = (uint8_t)(targetColorRaw);
    float tW = (uint8_t)(targetColorRaw >> 24);

    // フェード処理
    if (ledTransition == 1) {
        float step = 10.0;
        if (currentR < tR) currentR += step; else if (currentR > tR) currentR -= step;
        if (currentG < tG) currentG += step; else if (currentG > tG) currentG -= step;
        if (currentB < tB) currentB += step; else if (currentB > tB) currentB -= step;
        if (currentW < tW) currentW += step; else if (currentW > tW) currentW -= step;
        
        if (abs(currentR - tR) < step) currentR = tR;
        if (abs(currentG - tG) < step) currentG = tG;
        if (abs(currentB - tB) < step) currentB = tB;
        if (abs(currentW - tW) < step) currentW = tW;
    } else {
        currentR = tR; currentG = tG; currentB = tB; currentW = tW;
    }

    // エフェクト計算
    float brightnessFactor = 1.0;
    
    if (ledBrightnessMode == 1) brightnessFactor = 0.2;
    else if (ledBrightnessMode == 2) brightnessFactor = 1.0;
    else brightnessFactor = 0.0;

    if (ledEffect == 1) { // 点滅
        if ((now / 250) % 2 == 0) brightnessFactor = 0;
    } 
    else if (ledEffect == 2) { // 呼吸
        float val = (sin(now / 300.0) + 1.0) / 2.0;
        brightnessFactor *= val;
    }

    // 出力
    uint8_t finalR = (uint8_t)(currentR * brightnessFactor);
    uint8_t finalG = (uint8_t)(currentG * brightnessFactor);
    uint8_t finalB = (uint8_t)(currentB * brightnessFactor);
    uint8_t finalW = (uint8_t)(currentW * brightnessFactor);

    strip.setPixelColor(0, strip.Color(finalR, finalG, finalB, finalW));
    strip.show();
}

// ==========================================
// 13. 色定義
// ==========================================
void setupColors() {
    colors[0]  = strip.Color(255, 20, 100, 0);   // ピンク
    colors[1]  = strip.Color(255, 0, 0, 0);      // 赤
    colors[2]  = strip.Color(255, 100, 0, 0);    // オレンジ
    colors[3]  = strip.Color(255, 255, 0, 0);    // 黄色
    colors[4]  = strip.Color(150, 255, 0, 0);    // 黄緑
    colors[5]  = strip.Color(0, 255, 0, 0);      // 緑
    colors[6]  = strip.Color(0, 100, 0, 0);      // 深緑
    colors[7]  = strip.Color(0, 255, 255, 0);    // 水色
    colors[8]  = strip.Color(0, 0, 255, 0);      // 青
    colors[9]  = strip.Color(150, 0, 255, 0);    // 紫
    colors[10] = strip.Color(0, 0, 0, 255);      // 白 (Wのみ)
    colors[11] = strip.Color(0, 0, 0, 0);        // なし (消灯)
}
```

---

## 3.2 EffectStationコマンド定義

### 3.2.1 domain/model/EffectStationCommand.kt

```kotlin
package com.wildcard.fourd_at_home.domain.model

/**
 * EffectStation LED色定義
 */
enum class LedColor(val id: Int, val displayName: String, val emoji: String) {
    PINK(0, "ピンク", "💗"),
    RED(1, "赤", "🔴"),
    ORANGE(2, "オレンジ", "🟠"),
    YELLOW(3, "黄色", "🟡"),
    YELLOW_GREEN(4, "黄緑", "🟢"),
    GREEN(5, "緑", "🟢"),
    DARK_GREEN(6, "深緑", "🌲"),
    CYAN(7, "水色", "🩵"),
    BLUE(8, "青", "🔵"),
    PURPLE(9, "紫", "🟣"),
    WHITE(10, "白", "⚪"),
    OFF(11, "消灯", "⚫");

    companion object {
        fun fromId(id: Int): LedColor = entries.find { it.id == id } ?: OFF
    }
}

/**
 * LED明るさ
 */
enum class LedBrightness(val value: Int, val displayName: String) {
    OFF(0, "消灯"),
    LOW(1, "弱"),
    HIGH(2, "強");

    companion object {
        fun fromValue(value: Int): LedBrightness = 
            entries.find { it.value == value } ?: OFF
    }
}

/**
 * LEDエフェクト
 * 
 * JSON_SPECIFICATION.md準拠:
 * - flash mode: steady (0), slow_blink (1), fast_blink (3)
 */
enum class LedEffect(val value: Int, val displayName: String) {
    STEADY(0, "点灯"),           // flash: steady
    BLINK(1, "点滅"),            // flash: slow_blink
    BREATHE(2, "呼吸"),
    FAST_BLINK(3, "高速点滅");   // flash: fast_blink

    companion object {
        fun fromValue(value: Int): LedEffect = 
            entries.find { it.value == value } ?: STEADY
    }
}

/**
 * LED遷移モード
 */
enum class LedTransition(val value: Int, val displayName: String) {
    INSTANT(0, "即時"),
    FADE(1, "フェード");

    companion object {
        fun fromValue(value: Int): LedTransition = 
            entries.find { it.value == value } ?: INSTANT
    }
}

/**
 * EffectStationコマンドビルダー
 * 
 * JSON_SPECIFICATION.md準拠:
 * - effect: flash, color, water, wind, mist
 * - mode: 各エフェクト固有のモード文字列
 */
object EffectStationCommands {
    
    // =============================================================
    // JSON_SPECIFICATION.md準拠 コマンド
    // =============================================================
    
    // === WIND (風) - mode: burst ===
    fun fanOn(): String = "FAN,1"
    fun fanOff(): String = "FAN,0"
    
    // === WATER (水しぶき) - action: shot ===
    fun splash(): String = "SPLASH"
    
    // === MIST (ミスト) - action: shot ===
    fun mistOff(): String = "MIST,0"
    fun mistShot(): String = "MIST,1"
    fun mistOn(): String = "MIST,2"
    
    // === FLASH (白色LED点滅) ===
    // mode: steady - 常時点灯
    fun flashSteady(): String = led(
        color = LedColor.WHITE,
        brightness = LedBrightness.HIGH,
        effect = LedEffect.STEADY
    )
    
    // mode: slow_blink - 遅い点滅
    fun flashSlowBlink(): String = led(
        color = LedColor.WHITE,
        brightness = LedBrightness.HIGH,
        effect = LedEffect.BLINK  // 遅い点滅
    )
    
    // mode: fast_blink - 速い点滅
    fun flashFastBlink(): String = led(
        color = LedColor.WHITE,
        brightness = LedBrightness.HIGH,
        effect = LedEffect.FAST_BLINK  // 速い点滅
    )
    
    fun flashOff(): String = ledOff()
    
    // === COLOR (RGB LED) ===
    fun led(
        color: LedColor,
        brightness: LedBrightness = LedBrightness.HIGH,
        effect: LedEffect = LedEffect.STEADY,
        transition: LedTransition = LedTransition.INSTANT
    ): String {
        return "LED,${color.id},${brightness.value},${effect.value},${transition.value}"
    }
    
    fun ledOff(): String = led(LedColor.OFF, LedBrightness.OFF)
    
    fun ledColor(color: LedColor): String = led(
        color = color,
        brightness = LedBrightness.HIGH
    )
    
    fun ledBlink(color: LedColor): String = led(
        color = color,
        brightness = LedBrightness.HIGH,
        effect = LedEffect.BLINK
    )
    
    fun ledBreathe(color: LedColor): String = led(
        color = color,
        brightness = LedBrightness.HIGH,
        effect = LedEffect.BREATHE
    )
    
    // === 全停止 ===
    fun stopAll(): List<String> = listOf(
        fanOff(),
        mistOff(),
        ledOff()
    )
}
```

---

## 3.3 Android制御ロジック

### 3.3.1 domain/effect/EffectStationController.kt

```kotlin
package com.wildcard.fourd_at_home.domain.effect

import com.wildcard.fourd_at_home.ble.CommandSender
import com.wildcard.fourd_at_home.domain.model.ColorMode
import com.wildcard.fourd_at_home.domain.model.EffectStationCommands
import com.wildcard.fourd_at_home.domain.model.FlashMode
import com.wildcard.fourd_at_home.domain.model.LedBrightness
import com.wildcard.fourd_at_home.domain.model.LedColor
import com.wildcard.fourd_at_home.domain.model.LedEffect
import com.wildcard.fourd_at_home.domain.model.LedTransition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Effect Station制御コントローラー
 * 
 * JSON_SPECIFICATION.md準拠:
 * - effect: flash, color, water, wind, mist
 * - mode: 各エフェクト固有のモード文字列
 */
@Singleton
class EffectStationController @Inject constructor(
    private val commandSender: CommandSender
) {
    
    // =============================================================
    // JSON_SPECIFICATION.md準拠 エフェクト
    // =============================================================
    
    // === FLASH (白色LED点滅) ===
    // mode: steady, slow_blink, fast_blink
    suspend fun setFlash(mode: FlashMode): Result<Unit> {
        val command = when (mode) {
            FlashMode.STEADY -> EffectStationCommands.flashSteady()
            FlashMode.SLOW_BLINK -> EffectStationCommands.flashSlowBlink()
            FlashMode.FAST_BLINK -> EffectStationCommands.flashFastBlink()
        }
        return commandSender.sendToEffectStation(command)
    }
    
    suspend fun flashOff(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.flashOff())
    }
    
    // === COLOR (RGB LED) ===
    // mode: red, green, blue, yellow, cyan, purple
    suspend fun setColor(mode: ColorMode): Result<Unit> {
        val color = when (mode) {
            ColorMode.RED -> LedColor.RED
            ColorMode.GREEN -> LedColor.GREEN
            ColorMode.BLUE -> LedColor.BLUE
            ColorMode.YELLOW -> LedColor.YELLOW
            ColorMode.CYAN -> LedColor.CYAN
            ColorMode.PURPLE -> LedColor.PURPLE
        }
        return commandSender.sendToEffectStation(EffectStationCommands.ledColor(color))
    }
    
    suspend fun colorOff(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.ledOff())
    }
    
    // === WATER (水しぶき) ===
    // action: shot (瞬間的)
    suspend fun waterShot(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.splash())
    }
    
    // === WIND (風) ===
    // mode: burst
    suspend fun windOn(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.fanOn())
    }
    
    suspend fun windOff(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.fanOff())
    }
    
    // === MIST (ミスト) ===
    // action: shot (瞬間的)
    suspend fun mistShot(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.mistShot())
    }
    
    suspend fun mistOn(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.mistOn())
    }
    
    suspend fun mistOff(): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.mistOff())
    }
    
    // =============================================================
    // レガシー互換メソッド（旧形式サポート用）
    // =============================================================
    
    // === FAN (windの別名) ===
    suspend fun fanOn(): Result<Unit> = windOn()
    suspend fun fanOff(): Result<Unit> = windOff()
    suspend fun setFan(on: Boolean): Result<Unit> = if (on) windOn() else windOff()
    
    // === SPLASH (waterの別名) ===
    suspend fun splash(): Result<Unit> = waterShot()
    
    // === LED (color/flashの詳細制御) ===
    suspend fun setLed(
        color: LedColor,
        brightness: LedBrightness = LedBrightness.HIGH,
        effect: LedEffect = LedEffect.STEADY,
        transition: LedTransition = LedTransition.INSTANT
    ): Result<Unit> {
        val command = EffectStationCommands.led(color, brightness, effect, transition)
        return commandSender.sendToEffectStation(command)
    }
    
    suspend fun ledOff(): Result<Unit> = colorOff()
    
    suspend fun ledColor(color: LedColor): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.ledColor(color))
    }
    
    suspend fun ledBlink(color: LedColor): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.ledBlink(color))
    }
    
    suspend fun ledBreathe(color: LedColor): Result<Unit> {
        return commandSender.sendToEffectStation(EffectStationCommands.ledBreathe(color))
    }
    
    // === 全停止 ===
    suspend fun stopAll(): List<Result<Unit>> {
        return EffectStationCommands.stopAll().map { command ->
            commandSender.sendToEffectStation(command)
        }
    }
}
```

---

## 3.4 制御画面UI

### 3.4.1 ui/control/ControlViewModel.kt

```kotlin
package com.wildcard.fourd_at_home.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildcard.fourd_at_home.ble.BleConnection
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.CommandLogEntry
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.DeviceType
import com.wildcard.fourd_at_home.domain.effect.EffectStationController
import com.wildcard.fourd_at_home.domain.model.LedColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlUiState(
    val effectStationConnected: Boolean = false,
    val actionDrive1Connected: Boolean = false,
    val actionDrive2Connected: Boolean = false,
    val fanOn: Boolean = false,
    val commandLog: List<CommandLogEntry> = emptyList(),
    val isLogPaused: Boolean = false
)

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val bleDeviceManager: BleDeviceManager,
    private val effectStationController: EffectStationController
) : ViewModel() {
    
    private val _fanOn = MutableStateFlow(false)
    private val _isLogPaused = MutableStateFlow(false)
    
    val uiState: StateFlow<ControlUiState> = combine(
        bleDeviceManager.connections,
        bleDeviceManager.commandLog,
        _fanOn,
        _isLogPaused
    ) { connections, log, fanOn, isLogPaused ->
        ControlUiState(
            effectStationConnected = connections.values.any { 
                it.deviceType == DeviceType.EFFECT_STATION && it.state == ConnectionState.READY 
            },
            actionDrive1Connected = connections.values.any { 
                it.deviceType == DeviceType.ACTION_DRIVE_1 && it.state == ConnectionState.READY 
            },
            actionDrive2Connected = connections.values.any { 
                it.deviceType == DeviceType.ACTION_DRIVE_2 && it.state == ConnectionState.READY 
            },
            fanOn = fanOn,
            commandLog = if (isLogPaused) log else log.takeLast(50),
            isLogPaused = isLogPaused
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ControlUiState()
    )
    
    // === FAN ===
    fun toggleFan() {
        viewModelScope.launch {
            val newState = !_fanOn.value
            val result = effectStationController.setFan(newState)
            if (result.isSuccess) {
                _fanOn.value = newState
            }
        }
    }
    
    fun fanOn() {
        viewModelScope.launch {
            val result = effectStationController.fanOn()
            if (result.isSuccess) {
                _fanOn.value = true
            }
        }
    }
    
    fun fanOff() {
        viewModelScope.launch {
            val result = effectStationController.fanOff()
            if (result.isSuccess) {
                _fanOn.value = false
            }
        }
    }
    
    // === SPLASH ===
    fun splash() {
        viewModelScope.launch {
            effectStationController.splash()
        }
    }
    
    // === MIST ===
    fun mistOn() {
        viewModelScope.launch {
            effectStationController.mistOn()
        }
    }
    
    fun mistOff() {
        viewModelScope.launch {
            effectStationController.mistOff()
        }
    }
    
    fun mistShot() {
        viewModelScope.launch {
            effectStationController.mistShot()
        }
    }
    
    // === LED ===
    fun ledColor(color: LedColor) {
        viewModelScope.launch {
            effectStationController.ledColor(color)
        }
    }
    
    fun ledBlink(color: LedColor) {
        viewModelScope.launch {
            effectStationController.ledBlink(color)
        }
    }
    
    fun ledBreathe(color: LedColor) {
        viewModelScope.launch {
            effectStationController.ledBreathe(color)
        }
    }
    
    fun ledOff() {
        viewModelScope.launch {
            effectStationController.ledOff()
        }
    }
    
    // === 全停止 ===
    fun stopAll() {
        viewModelScope.launch {
            effectStationController.stopAll()
            _fanOn.value = false
        }
    }
    
    // === ログ ===
    fun toggleLogPause() {
        _isLogPaused.value = !_isLogPaused.value
    }
    
    fun clearLog() {
        bleDeviceManager.clearLog()
    }
}
```

### 3.4.2 ui/control/ControlScreen.kt

```kotlin
package com.wildcard.fourd_at_home.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wildcard.fourd_at_home.ble.CommandDirection
import com.wildcard.fourd_at_home.ble.CommandLogEntry
import com.wildcard.fourd_at_home.ble.CommandStatus
import com.wildcard.fourd_at_home.domain.model.LedColor
import com.wildcard.fourd_at_home.ui.theme.EffectCyan
import com.wildcard.fourd_at_home.ui.theme.EffectGreen
import com.wildcard.fourd_at_home.ui.theme.EffectOrange
import com.wildcard.fourd_at_home.ui.theme.NeonRed
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlScreen(
    viewModel: ControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左側: 手動制御パネル
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 接続状態
            ConnectionStatusBar(
                effectStationConnected = uiState.effectStationConnected,
                actionDrive1Connected = uiState.actionDrive1Connected,
                actionDrive2Connected = uiState.actionDrive2Connected
            )
            
            // EffectStation制御
            if (uiState.effectStationConnected) {
                EffectStationControlPanel(
                    fanOn = uiState.fanOn,
                    onFanToggle = viewModel::toggleFan,
                    onSplash = viewModel::splash,
                    onMistShot = viewModel::mistShot,
                    onMistOn = viewModel::mistOn,
                    onMistOff = viewModel::mistOff,
                    onLedColor = viewModel::ledColor,
                    onLedBlink = viewModel::ledBlink,
                    onLedOff = viewModel::ledOff,
                    onStopAll = viewModel::stopAll
                )
            } else {
                NoDeviceConnectedCard(deviceName = "EffectStation")
            }
        }
        
        // 右側: 通信ログ
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            CommandLogPanel(
                logs = uiState.commandLog,
                isPaused = uiState.isLogPaused,
                onTogglePause = viewModel::toggleLogPause,
                onClear = viewModel::clearLog
            )
        }
    }
}

@Composable
private fun ConnectionStatusBar(
    effectStationConnected: Boolean,
    actionDrive1Connected: Boolean,
    actionDrive2Connected: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectionChip("EffectStation", effectStationConnected)
        ConnectionChip("Motor1", actionDrive1Connected)
        ConnectionChip("Motor2", actionDrive2Connected)
    }
}

@Composable
private fun ConnectionChip(name: String, connected: Boolean) {
    val color = if (connected) StatusConnected else Color.Gray
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectStationControlPanel(
    fanOn: Boolean,
    onFanToggle: () -> Unit,
    onSplash: () -> Unit,
    onMistShot: () -> Unit,
    onMistOn: () -> Unit,
    onMistOff: () -> Unit,
    onLedColor: (LedColor) -> Unit,
    onLedBlink: (LedColor) -> Unit,
    onLedOff: () -> Unit,
    onStopAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "EffectStation 制御",
            style = MaterialTheme.typography.titleMedium
        )
        
        // 風 (FAN)
        EffectCard(
            title = "💨 風 (FAN)",
            color = EffectGreen
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onFanToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (fanOn) EffectGreen else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(if (fanOn) "ON" else "OFF")
                }
            }
        }
        
        // 水 (SPLASH/MIST)
        EffectCard(
            title = "💦 水",
            color = EffectCyan
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSplash) { Text("水しぶき") }
                Button(onClick = onMistShot) { Text("ミスト(瞬間)") }
                FilledTonalButton(onClick = onMistOn) { Text("ミストON") }
                OutlinedButton(onClick = onMistOff) { Text("ミストOFF") }
            }
        }
        
        // LED
        EffectCard(
            title = "💡 LED",
            color = EffectOrange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 色選択
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LedColor.entries.filter { it != LedColor.OFF }.forEach { color ->
                        FilledTonalButton(
                            onClick = { onLedColor(color) },
                            modifier = Modifier.size(48.dp),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Text(color.emoji, fontSize = 16.sp)
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLedBlink(LedColor.RED) }) { Text("点滅(赤)") }
                    OutlinedButton(onClick = onLedOff) { Text("消灯") }
                }
            }
        }
        
        // 全停止
        Button(
            onClick = onStopAll,
            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("全停止")
        }
    }
}

@Composable
private fun EffectCard(
    title: String,
    color: Color,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
            content()
        }
    }
}

@Composable
private fun NoDeviceConnectedCard(deviceName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$deviceName が接続されていません",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "設定画面からデバイスを接続してください",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommandLogPanel(
    logs: List<CommandLogEntry>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    // 自動スクロール
    LaunchedEffect(logs.size) {
        if (!isPaused && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 通信ログ",
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "再開" else "一時停止"
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Delete, contentDescription = "クリア")
                }
            }
        }
        
        // ログ表示
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D0D0D)
            )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { entry ->
                    LogEntryRow(entry = entry, dateFormat = dateFormat)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: CommandLogEntry,
    dateFormat: SimpleDateFormat
) {
    val directionColor = when (entry.direction) {
        CommandDirection.TX -> Color(0xFF4CAF50)
        CommandDirection.RX -> Color(0xFF2196F3)
    }
    val statusColor = when (entry.status) {
        CommandStatus.SUCCESS -> Color(0xFF4CAF50)
        CommandStatus.PENDING -> Color(0xFFFFC107)
        CommandStatus.FAILED -> Color(0xFFF44336)
        CommandStatus.TIMEOUT -> Color(0xFFFF9800)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "[${dateFormat.format(Date(entry.timestamp))}]",
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (entry.direction == CommandDirection.TX) "→" else "←",
            color = directionColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = entry.command,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when (entry.status) {
                CommandStatus.SUCCESS -> "✓"
                CommandStatus.PENDING -> "..."
                CommandStatus.FAILED -> "✗"
                CommandStatus.TIMEOUT -> "⏱"
            },
            color = statusColor,
            fontSize = 11.sp
        )
    }
}
```

---

## 3.5 動作テスト

### テスト項目チェックリスト

| # | テスト項目 | 手順 | 期待結果 |
|:-:|:-----------|:-----|:---------|
| 1 | ESP32起動確認 | ESP32の電源投入、シリアルモニタ確認 | デバイス名表示、アドバタイズ開始 |
| 2 | BLEスキャン | Android設定画面でスキャン | 4D_ES_XXXXが検出される |
| 3 | BLE接続 | デバイスの接続ボタンタップ | READY状態になる |
| 4 | FAN ON | 制御画面でFAN ONボタン | 扇風機が回る、ログに表示 |
| 5 | FAN OFF | 制御画面でFAN OFFボタン | 扇風機が止まる |
| 6 | SPLASH | 制御画面で水しぶきボタン | 200ms間水が出る |
| 7 | MIST Shot | 制御画面でミスト(瞬間)ボタン | 一瞬ミストが出る |
| 8 | MIST ON/OFF | 制御画面でミストON/OFFボタン | ミストが継続/停止 |
| 9 | LED色変更 | 制御画面で色ボタンタップ | LEDが指定色で点灯 |
| 10 | LED点滅 | 制御画面で点滅ボタン | LEDが点滅 |
| 11 | LED消灯 | 制御画面で消灯ボタン | LEDが消える |
| 12 | 全停止 | 制御画面で全停止ボタン | FAN/MIST/LED全て停止 |
| 13 | 切断・再接続 | 設定画面で切断後、再接続 | 正常に再接続できる |

---

## ✅ Phase 3 完了チェックリスト

- [ ] ESP32 BLEファームウェアが動作する
- [ ] AndroidからBLE接続できる
- [ ] FAN ON/OFF が動作する
- [ ] SPLASH が動作する
- [ ] MIST が動作する
- [ ] LED色変更が動作する
- [ ] LED点滅が動作する
- [ ] 全停止が動作する
- [ ] 通信ログが表示される

---

## 📝 次のPhase

[Phase 4: ActionDrive実装](./04_PHASE4_ACTION_DRIVE.md) へ進む
