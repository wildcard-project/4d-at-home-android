/**
 * 4D@HOME ActionDrive Motor1 ESP32 ファームウェア
 * 
 * BLE経由でコマンドを受信し、振動モーターを制御
 * Motor1: 座席左側/前方振動用
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// === ピン定義 ===
#define PIN_MOTOR     25    // モーター制御 (PWM)
#define PIN_LED       2     // 状態表示LED (オンボード)

// === PWM設定 ===
#define PWM_CHANNEL   0
#define PWM_FREQ      20000  // 20kHz (モーター向け高周波)
#define PWM_RESOLUTION 8

// === BLE UUIDs ===
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// === コマンド定義 ===
#define CMD_VIBRATION 0x10
#define CMD_STOP      0x00

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;

bool deviceConnected = false;
bool oldDeviceConnected = false;
uint8_t currentIntensity = 0;

// デバイス名生成
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    sprintf(name, "4D_AD1_%02X%02X", mac[4], mac[5]);
    return String(name);
}

// === モーター制御 ===

void setMotor(uint8_t intensity) {
    currentIntensity = intensity;
    ledcWrite(PWM_CHANNEL, intensity);
    
    // 状態LED
    digitalWrite(PIN_LED, intensity > 0 ? HIGH : LOW);
    
    Serial.printf("Motor1: %d\n", intensity);
}

void stopMotor() {
    setMotor(0);
    Serial.println("Motor1 stopped");
}

// ステータス送信
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[2] = {
            0x01,  // Motor1識別子
            currentIntensity
        };
        pStatusChar->setValue(status, 2);
        pStatusChar->notify();
    }
}

// === コマンド処理 ===

void processCommand(const uint8_t* data, size_t length) {
    if (length < 1) return;
    
    uint8_t cmd = data[0];
    
    switch (cmd) {
        case CMD_VIBRATION:
            if (length >= 2) {
                setMotor(data[1]);
            }
            break;
            
        case CMD_STOP:
            stopMotor();
            break;
            
        default:
            // 直接強度値として解釈
            setMotor(cmd);
            break;
    }
    
    sendStatus();
}

// === BLEコールバック ===

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("Device connected");
        
        // 接続時LED点滅
        for (int i = 0; i < 2; i++) {
            digitalWrite(PIN_LED, HIGH);
            delay(100);
            digitalWrite(PIN_LED, LOW);
            delay(100);
        }
    }
    
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("Device disconnected");
        
        // 安全のためモーター停止
        stopMotor();
    }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) {
        String value = pCharacteristic->getValue();
        if (value.length() > 0) {
            Serial.printf("Received: ");
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
    Serial.println("4D@HOME ActionDrive Motor1 starting...");
    
    // GPIO設定
    pinMode(PIN_LED, OUTPUT);
    digitalWrite(PIN_LED, LOW);
    
    // PWM設定
    ledcSetup(PWM_CHANNEL, PWM_FREQ, PWM_RESOLUTION);
    ledcAttachPin(PIN_MOTOR, PWM_CHANNEL);
    
    // 初期状態: モーター停止
    stopMotor();
    
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
        digitalWrite(PIN_LED, HIGH);
        delay(150);
        digitalWrite(PIN_LED, LOW);
        delay(150);
    }
}

// === メインループ ===

void loop() {
    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        pServer->startAdvertising();
        Serial.println("Advertising restarted");
        oldDeviceConnected = deviceConnected;
    }
    
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }
    
    delay(10);
}