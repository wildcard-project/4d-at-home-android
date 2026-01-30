/**
 * 4D@HOME EffectStation ESP32 ファームウェア
 * JSON_SPECIFICATION.md準拠の文字列コマンド対応
 * 
 * BLE経由でコマンドを受信し、以下のエフェクトを制御:
 * - FAN (ファン): "FAN,0" / "FAN,1"
 * - SPLASH (水噴射): "SPLASH"
 * - MIST (ミスト): "MIST,0" / "MIST,1" / "MIST,2"
 * - LED (NeoPixel): "LED,colorId,brightness,effect,transition"
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Adafruit_NeoPixel.h>

// === ピン定義（JSON_SPECIFICATION.md準拠）===
#define PIN_FAN       25    // ファン制御 (GPIO25)
#define PIN_SPLASH    26    // 水噴射制御 (GPIO26)
#define PIN_LED       27    // NeoPixel LED (GPIO27)
#define PIN_MIST      32    // ミスト制御 (GPIO32)
#define NUM_LEDS      16    // LED数

// === PWMチャンネル ===
#define PWM_CHANNEL_FAN    0
#define PWM_CHANNEL_SPLASH 1
#define PWM_CHANNEL_MIST   2
#define PWM_FREQ           5000
#define PWM_RESOLUTION     8

// === BLE UUIDs ===
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// === 色定義（JSON_SPECIFICATION.md準拠）===
// colorId: 0=PINK, 1=RED, 2=LIME, 3=ORANGE, 4=YELLOW, 5=GREEN,
//          6=CYAN, 7=BLUE, 8=INDIGO, 9=PURPLE, 10=WHITE, 11=OFF
const uint8_t COLOR_TABLE[12][3] = {
    {255, 105, 180},  // 0: PINK
    {255, 0, 0},      // 1: RED
    {50, 205, 50},    // 2: LIME
    {255, 165, 0},    // 3: ORANGE
    {255, 255, 0},    // 4: YELLOW
    {0, 255, 0},      // 5: GREEN
    {0, 255, 255},    // 6: CYAN
    {0, 0, 255},      // 7: BLUE
    {75, 0, 130},     // 8: INDIGO
    {128, 0, 128},    // 9: PURPLE
    {255, 255, 255},  // 10: WHITE
    {0, 0, 0}         // 11: OFF
};

// 明るさレベル
const uint8_t BRIGHTNESS_LEVELS[3] = {64, 128, 255};  // 弱, 中, 強

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;
Adafruit_NeoPixel strip(NUM_LEDS, PIN_LED, NEO_GRB + NEO_KHZ800);

bool deviceConnected = false;
bool oldDeviceConnected = false;

// LED点滅制御
bool ledBlinking = false;
int ledBlinkMode = 0;  // 0=点灯, 1=ゆっくり点滅, 2=速い点滅
unsigned long lastBlinkTime = 0;
bool ledBlinkState = true;
uint8_t currentColorId = 11;
uint8_t currentBrightness = 0;

// ミスト制御
int mistMode = 0;  // 0=OFF, 1=shot, 2=継続
unsigned long mistShotStartTime = 0;
const unsigned long MIST_SHOT_DURATION = 500;  // 一瞬モードの持続時間(ms)

// 現在のエフェクト状態
struct EffectState {
    bool fanOn = false;
    bool splashActive = false;
    int mistMode = 0;
    uint8_t colorId = 11;
    uint8_t brightness = 0;
    int ledEffect = 0;
} currentState;

// デバイス名生成
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    sprintf(name, "4D_ES_%02X%02X", mac[4], mac[5]);
    return String(name);
}

// === エフェクト制御関数 ===

void setFan(bool on) {
    currentState.fanOn = on;
    ledcWrite(PWM_CHANNEL_FAN, on ? 255 : 0);
    Serial.printf("FAN: %s\n", on ? "ON" : "OFF");
}

void triggerSplash() {
    currentState.splashActive = true;
    ledcWrite(PWM_CHANNEL_SPLASH, 255);
    Serial.println("SPLASH triggered");
    
    // 短時間後に自動OFF
    delay(200);
    ledcWrite(PWM_CHANNEL_SPLASH, 0);
    currentState.splashActive = false;
}

void setMist(int mode) {
    currentState.mistMode = mode;
    mistMode = mode;
    
    switch (mode) {
        case 0:  // OFF
            ledcWrite(PWM_CHANNEL_MIST, 0);
            Serial.println("MIST: OFF");
            break;
        case 1:  // Shot（一瞬）
            ledcWrite(PWM_CHANNEL_MIST, 255);
            mistShotStartTime = millis();
            Serial.println("MIST: SHOT");
            break;
        case 2:  // 継続
            ledcWrite(PWM_CHANNEL_MIST, 255);
            Serial.println("MIST: CONTINUOUS");
            break;
    }
}

void setLedColor(uint8_t colorId, uint8_t brightnessLevel, int effect, int transition) {
    currentColorId = colorId;
    currentState.colorId = colorId;
    currentState.ledEffect = effect;
    
    if (colorId >= 12) colorId = 11;  // 無効なIDはOFF
    if (brightnessLevel >= 3) brightnessLevel = 2;
    
    currentBrightness = (colorId == 11) ? 0 : BRIGHTNESS_LEVELS[brightnessLevel];
    currentState.brightness = currentBrightness;
    
    // 点滅モード設定
    ledBlinkMode = effect;
    ledBlinking = (effect != 0);
    ledBlinkState = true;
    
    // 即時適用（transition=0）またはフェード（transition=1、簡易実装）
    strip.setBrightness(currentBrightness);
    for (int i = 0; i < NUM_LEDS; i++) {
        strip.setPixelColor(i, strip.Color(
            COLOR_TABLE[colorId][0],
            COLOR_TABLE[colorId][1],
            COLOR_TABLE[colorId][2]
        ));
    }
    strip.show();
    
    Serial.printf("LED: colorId=%d, brightness=%d, effect=%d, transition=%d\n", 
                  colorId, brightnessLevel, effect, transition);
}

void allOff() {
    setFan(false);
    setMist(0);
    setLedColor(11, 0, 0, 0);
    Serial.println("All effects OFF");
}

// ステータス送信
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[8] = {
            currentState.fanOn ? 1 : 0,
            currentState.splashActive ? 1 : 0,
            (uint8_t)currentState.mistMode,
            currentState.colorId,
            currentState.brightness,
            (uint8_t)currentState.ledEffect,
            0x00,
            0x00
        };
        pStatusChar->setValue(status, 8);
        pStatusChar->notify();
    }
}

// === 文字列コマンド処理（JSON_SPECIFICATION.md準拠）===

void processStringCommand(const String& cmd) {
    Serial.printf("Processing command: %s\n", cmd.c_str());
    
    // コマンドをカンマで分割
    int firstComma = cmd.indexOf(',');
    String cmdType = (firstComma > 0) ? cmd.substring(0, firstComma) : cmd;
    cmdType.trim();
    cmdType.toUpperCase();
    
    if (cmdType == "FAN") {
        if (firstComma > 0) {
            int value = cmd.substring(firstComma + 1).toInt();
            setFan(value != 0);
        }
    }
    else if (cmdType == "SPLASH") {
        triggerSplash();
    }
    else if (cmdType == "MIST") {
        if (firstComma > 0) {
            int mode = cmd.substring(firstComma + 1).toInt();
            setMist(mode);
        }
    }
    else if (cmdType == "LED") {
        // LED,colorId,brightness,effect,transition
        int values[4] = {11, 0, 0, 0};  // デフォルト値
        int idx = 0;
        int start = firstComma + 1;
        
        for (int i = start; i < cmd.length() && idx < 4; i++) {
            int comma = cmd.indexOf(',', i);
            if (comma < 0) comma = cmd.length();
            values[idx++] = cmd.substring(i, comma).toInt();
            i = comma;
        }
        
        setLedColor(values[0], values[1], values[2], values[3]);
    }
    else if (cmdType == "OFF" || cmdType == "ALL_OFF") {
        allOff();
    }
    else {
        Serial.printf("Unknown command: %s\n", cmdType.c_str());
    }
    
    sendStatus();
}

// === BLEコールバック ===

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("Device connected");
    }
    
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("Device disconnected");
        
        // 安全のため全エフェクトOFF
        allOff();
    }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) {
        String value = pCharacteristic->getValue();
        if (value.length() > 0) {
            Serial.printf("Received: ");
            for (int i = 0; i < value.length(); i++) {
                Serial.printf("%c", value[i]);
            }
            Serial.println();
            
            // 文字列コマンドとして処理
            processStringCommand(value);
        }
    }
};

// === セットアップ ===

void setup() {
    Serial.begin(115200);
    Serial.println("4D@HOME EffectStation starting...");
    Serial.println("JSON_SPECIFICATION.md compliant (String commands)");
    
    // PWM設定
    ledcSetup(PWM_CHANNEL_FAN, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_SPLASH, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_MIST, PWM_FREQ, PWM_RESOLUTION);
    
    ledcAttachPin(PIN_FAN, PWM_CHANNEL_FAN);
    ledcAttachPin(PIN_SPLASH, PWM_CHANNEL_SPLASH);
    ledcAttachPin(PIN_MIST, PWM_CHANNEL_MIST);
    
    // NeoPixel初期化
    strip.begin();
    strip.show();
    
    // 初期状態: 全OFF
    allOff();
    
    // BLE初期化
    String deviceName = getDeviceName();
    Serial.printf("Device name: %s\n", deviceName.c_str());
    
    BLEDevice::init(deviceName.c_str());
    
    // サーバー作成
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());
    
    // サービス作成
    BLEService* pService = pServer->createService(SERVICE_UUID);
    
    // Command Characteristic (Write)
    pCommandChar = pService->createCharacteristic(
        COMMAND_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE | 
        BLECharacteristic::PROPERTY_WRITE_NR
    );
    pCommandChar->setCallbacks(new CommandCallbacks());
    
    // Status Characteristic (Notify)
    pStatusChar = pService->createCharacteristic(
        STATUS_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | 
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusChar->addDescriptor(new BLE2902());
    
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
    
    // 起動完了表示（LED緑点滅）
    for (int i = 0; i < 3; i++) {
        setLedColor(5, 1, 0, 0);  // GREEN, 中
        delay(200);
        setLedColor(11, 0, 0, 0);  // OFF
        delay(200);
    }
}

// === メインループ ===

void loop() {
    // ミストのshot自動OFF
    if (mistMode == 1 && (millis() - mistShotStartTime > MIST_SHOT_DURATION)) {
        setMist(0);
    }
    
    // LED点滅処理
    if (ledBlinking && currentColorId != 11) {
        unsigned long blinkInterval = (ledBlinkMode == 1) ? 1000 : 200;  // ゆっくり: 1秒, 速い: 0.2秒
        
        if (millis() - lastBlinkTime > blinkInterval) {
            lastBlinkTime = millis();
            ledBlinkState = !ledBlinkState;
            
            if (ledBlinkState) {
                strip.setBrightness(currentBrightness);
            } else {
                strip.setBrightness(0);
            }
            strip.show();
        }
    }
    
    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);  // Bluetoothスタックに時間を与える
        pServer->startAdvertising();
        Serial.println("Advertising restarted");
        oldDeviceConnected = deviceConnected;
    }
    
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }
    
    delay(10);
}
}