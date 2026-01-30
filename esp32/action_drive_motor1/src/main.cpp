/**
 * 4D@HOME ActionDrive Motor1 ESP32 ファームウェア
 * JSON_SPECIFICATION.md準拠の文字列コマンド対応
 * 
 * BLE経由でコマンドを受信し、振動モーターを制御
 * Motor1: 座席左側/前方振動用
 * 
 * コマンド形式: "MOTOR,mode_name"
 * mode: up_weak, up, up_strong, down_weak, down, down_strong,
 *       left_weak, left, left_strong, right_weak, right, right_strong,
 *       heartbeat, OFF
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

// === 振動モード定義（JSON_SPECIFICATION.md準拠）===
// Motor1は上方向と左方向を担当
struct VibrationPattern {
    uint8_t intensity;      // PWM強度 (0-255)
    uint16_t onTime;        // ON時間 (ms)
    uint16_t offTime;       // OFF時間 (ms)
    bool continuous;        // 連続振動か
};

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;

bool deviceConnected = false;
bool oldDeviceConnected = false;
String currentMode = "OFF";
uint8_t currentIntensity = 0;

// パターン再生用
VibrationPattern currentPattern = {0, 0, 0, false};
bool patternActive = false;
unsigned long patternStartTime = 0;
bool patternPhase = true;  // true=ON, false=OFF

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
}

void stopMotor() {
    patternActive = false;
    currentMode = "OFF";
    setMotor(0);
    Serial.println("Motor1 stopped");
}

// 振動パターンを開始
void startPattern(VibrationPattern pattern, const String& modeName) {
    currentMode = modeName;
    currentPattern = pattern;
    
    if (pattern.continuous) {
        // 連続振動
        patternActive = false;
        setMotor(pattern.intensity);
    } else {
        // パターン振動
        patternActive = true;
        patternPhase = true;
        patternStartTime = millis();
        setMotor(pattern.intensity);
    }
    
    Serial.printf("Motor1 pattern: %s, intensity=%d\n", modeName.c_str(), pattern.intensity);
}

// ステータス送信
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[4] = {
            0x01,  // Motor1識別子
            currentIntensity,
            (uint8_t)(patternActive ? 1 : 0),
            0x00
        };
        pStatusChar->setValue(status, 4);
        pStatusChar->notify();
    }
}

// === 文字列コマンド処理（JSON_SPECIFICATION.md準拠）===

void processStringCommand(const String& cmd) {
    Serial.printf("Motor1 received: %s\n", cmd.c_str());
    
    // コマンドをカンマで分割
    int comma = cmd.indexOf(',');
    String cmdType = (comma > 0) ? cmd.substring(0, comma) : cmd;
    String mode = (comma > 0) ? cmd.substring(comma + 1) : "";
    cmdType.trim();
    cmdType.toUpperCase();
    mode.trim();
    mode.toLowerCase();
    
    if (cmdType == "MOTOR" || cmdType == "VIB" || cmdType == "VIBRATION") {
        // モード名から振動パターンを決定
        VibrationPattern pattern;
        
        if (mode == "off" || mode == "") {
            stopMotor();
            return;
        }
        // 上方向 (Motor1が担当)
        else if (mode == "up_weak") {
            pattern = {64, 0, 0, true};  // 弱い連続振動
        }
        else if (mode == "up") {
            pattern = {150, 0, 0, true};  // 中程度の連続振動
        }
        else if (mode == "up_strong") {
            pattern = {255, 0, 0, true};  // 強い連続振動
        }
        // 下方向 (Motor1も対応可能、バックアップ)
        else if (mode == "down_weak") {
            pattern = {48, 0, 0, true};
        }
        else if (mode == "down") {
            pattern = {120, 0, 0, true};
        }
        else if (mode == "down_strong") {
            pattern = {200, 0, 0, true};
        }
        // 左方向 (Motor1が担当)
        else if (mode == "left_weak") {
            pattern = {64, 300, 200, false};  // パターン振動
        }
        else if (mode == "left") {
            pattern = {150, 300, 200, false};
        }
        else if (mode == "left_strong") {
            pattern = {255, 300, 200, false};
        }
        // 右方向 (Motor1バックアップ)
        else if (mode == "right_weak") {
            pattern = {64, 300, 200, false};
        }
        else if (mode == "right") {
            pattern = {150, 300, 200, false};
        }
        else if (mode == "right_strong") {
            pattern = {255, 300, 200, false};
        }
        // 特殊パターン
        else if (mode == "heartbeat") {
            pattern = {200, 150, 100, false};  // ドクドク
        }
        else {
            Serial.printf("Unknown mode: %s\n", mode.c_str());
            return;
        }
        
        startPattern(pattern, mode);
    }
    else if (cmdType == "OFF" || cmdType == "STOP") {
        stopMotor();
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
        std::string stdValue = pCharacteristic->getValue();
        String value = String(stdValue.c_str());
        if (value.length() > 0) {
            Serial.printf("Received: %s\n", value.c_str());
            
            // 文字列コマンドとして処理
            processStringCommand(value);
        }
    }
};

// === セットアップ ===

void setup() {
    Serial.begin(115200);
    Serial.println("4D@HOME ActionDrive Motor1 starting...");
    Serial.println("JSON_SPECIFICATION.md compliant (String commands)");
    
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
    // パターン振動の処理
    if (patternActive) {
        unsigned long elapsed = millis() - patternStartTime;
        
        if (patternPhase) {
            // ON期間
            if (elapsed >= currentPattern.onTime) {
                patternPhase = false;
                patternStartTime = millis();
                setMotor(0);
            }
        } else {
            // OFF期間
            if (elapsed >= currentPattern.offTime) {
                patternPhase = true;
                patternStartTime = millis();
                setMotor(currentPattern.intensity);
            }
        }
    }
    
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