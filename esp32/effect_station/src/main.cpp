/**
 * 4D@HOME EffectStation ESP32 ファームウェア
 * 
 * BLE経由でコマンドを受信し、以下のエフェクトを制御:
 * - Fan (ファン)
 * - Water (水噴射)
 * - Mist (ミスト)
 * - LED (NeoPixel RGB LED)
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Adafruit_NeoPixel.h>

// === ピン定義 ===
#define PIN_FAN       25    // ファン制御 (PWM)
#define PIN_WATER     26    // 水噴射制御 (PWM)
#define PIN_MIST      27    // ミスト制御 (PWM)
#define PIN_LED       32    // NeoPixel LED
#define NUM_LEDS      16    // LED数

// === PWMチャンネル ===
#define PWM_CHANNEL_FAN   0
#define PWM_CHANNEL_WATER 1
#define PWM_CHANNEL_MIST  2
#define PWM_FREQ          5000
#define PWM_RESOLUTION    8

// === BLE UUIDs ===
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// === コマンド定義 ===
#define CMD_FAN     0x01
#define CMD_WATER   0x02
#define CMD_MIST    0x03
#define CMD_LED     0x04
#define CMD_ALL_OFF 0xFF

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;
Adafruit_NeoPixel strip(NUM_LEDS, PIN_LED, NEO_GRB + NEO_KHZ800);

bool deviceConnected = false;
bool oldDeviceConnected = false;

// 現在のエフェクト状態
struct EffectState {
    uint8_t fan = 0;
    uint8_t water = 0;
    uint8_t mist = 0;
    uint8_t ledR = 0;
    uint8_t ledG = 0;
    uint8_t ledB = 0;
    uint8_t ledBrightness = 0;
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

void setFan(uint8_t intensity) {
    currentState.fan = intensity;
    ledcWrite(PWM_CHANNEL_FAN, intensity);
    Serial.printf("Fan: %d\n", intensity);
}

void setWater(uint8_t intensity) {
    currentState.water = intensity;
    ledcWrite(PWM_CHANNEL_WATER, intensity);
    Serial.printf("Water: %d\n", intensity);
}

void setMist(uint8_t intensity) {
    currentState.mist = intensity;
    ledcWrite(PWM_CHANNEL_MIST, intensity);
    Serial.printf("Mist: %d\n", intensity);
}

void setLed(uint8_t r, uint8_t g, uint8_t b, uint8_t brightness) {
    currentState.ledR = r;
    currentState.ledG = g;
    currentState.ledB = b;
    currentState.ledBrightness = brightness;
    
    strip.setBrightness(brightness);
    for (int i = 0; i < NUM_LEDS; i++) {
        strip.setPixelColor(i, strip.Color(r, g, b));
    }
    strip.show();
    Serial.printf("LED: R=%d, G=%d, B=%d, Brightness=%d\n", r, g, b, brightness);
}

void allOff() {
    setFan(0);
    setWater(0);
    setMist(0);
    setLed(0, 0, 0, 0);
    Serial.println("All effects OFF");
}

// ステータス送信
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[8] = {
            currentState.fan,
            currentState.water,
            currentState.mist,
            currentState.ledR,
            currentState.ledG,
            currentState.ledB,
            currentState.ledBrightness,
            0x00  // 予備
        };
        pStatusChar->setValue(status, 8);
        pStatusChar->notify();
    }
}

// === コマンド処理 ===

void processCommand(const uint8_t* data, size_t length) {
    if (length < 1) return;
    
    uint8_t cmd = data[0];
    
    switch (cmd) {
        case CMD_FAN:
            if (length >= 2) {
                setFan(data[1]);
            }
            break;
            
        case CMD_WATER:
            if (length >= 2) {
                setWater(data[1]);
            }
            break;
            
        case CMD_MIST:
            if (length >= 2) {
                setMist(data[1]);
            }
            break;
            
        case CMD_LED:
            if (length >= 5) {
                setLed(data[1], data[2], data[3], data[4]);
            } else if (length >= 4) {
                setLed(data[1], data[2], data[3], 255);
            }
            break;
            
        case CMD_ALL_OFF:
            allOff();
            break;
            
        default:
            Serial.printf("Unknown command: 0x%02X\n", cmd);
            break;
    }
    
    // ステータス送信
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
            Serial.printf("Received command: ");
            for (int i = 0; i < value.length(); i++) {
                Serial.printf("%02X ", (uint8_t)value[i]);
            }
            Serial.println();
            
            processCommand((const uint8_t*)value.c_str(), value.length());
        }
    }
};

// === セットアップ ===

void setup() {
    Serial.begin(115200);
    Serial.println("4D@HOME EffectStation starting...");
    
    // PWM設定
    ledcSetup(PWM_CHANNEL_FAN, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_WATER, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(PWM_CHANNEL_MIST, PWM_FREQ, PWM_RESOLUTION);
    
    ledcAttachPin(PIN_FAN, PWM_CHANNEL_FAN);
    ledcAttachPin(PIN_WATER, PWM_CHANNEL_WATER);
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
    
    // 起動完了表示（LED点滅）
    for (int i = 0; i < 3; i++) {
        setLed(0, 255, 0, 128);
        delay(200);
        setLed(0, 0, 0, 0);
        delay(200);
    }
}

// === メインループ ===

void loop() {
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