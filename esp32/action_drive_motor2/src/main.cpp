/**
 * 4D@HOME ActionDrive Motor2 ESP32 ファームウェア
 * MQTT版互換 - 4ピン構成・デジタルON/OFF制御
 * 
 * BLE経由でコマンドを受信し、振動モーターを制御
 * Motor2: 座席右側/後方振動用
 * 
 * コマンド形式: "MOTOR,mode_name"
 * mode: STRONG, MEDIUM_STRONG, MEDIUM_WEAK, WEAK, 
 *       HEARTBEAT, RUMBLE_FAST, RUMBLE_SLOW, OFF
 * 
 * ピン構成（MQTT版と同一）:
 * - GPIO 14 (D5): 振動 強 (STRONG)
 * - GPIO 12 (D6): 振動 中強 (MEDIUM_STRONG)
 * - GPIO 13 (D7): 振動 中強/中弱 (MEDIUM_STRONG/MEDIUM_WEAK)
 * - GPIO 15 (D8): 振動 弱 (WEAK)
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// === ピン定義（MQTT版と同一）===
#define MOTOR_PIN_D5  14    // 振動 強 (STRONG)
#define MOTOR_PIN_D6  12    // 振動 中強 (MEDIUM_STRONG)
#define MOTOR_PIN_D7  13    // 振動 中強/中弱 (MEDIUM_STRONG/MEDIUM_WEAK)
#define MOTOR_PIN_D8  15    // 振動 弱 (WEAK)
#define PIN_LED       2     // 状態表示LED (オンボード)

// === BLE UUIDs ===
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// === モーター制御モード（MQTT版と同一）===
enum MotorMode {
    MOTOR_OFF,
    MOTOR_WEAK,
    MOTOR_MEDIUM_WEAK,
    MOTOR_MEDIUM_STRONG,
    MOTOR_STRONG,
    MOTOR_HEARTBEAT,
    MOTOR_RUMBLE_FAST,
    MOTOR_RUMBLE_SLOW
};

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;

bool deviceConnected = false;
bool oldDeviceConnected = false;
String currentModeName = "OFF";
MotorMode currentMotorMode = MOTOR_OFF;

// ノンブロッキング制御用タイマー
unsigned long lastPatternTime = 0;
int patternStep = 0;

// デバイス名生成
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    sprintf(name, "4D_AD2_%02X%02X", mac[4], mac[5]);
    return String(name);
}

// === モーター制御ヘルパー（MQTT版と同一ロジック）===

/**
 * @brief 全モーターの ON/OFF を一括設定
 */
void setAllMotors(bool state) {
    digitalWrite(MOTOR_PIN_D5, state);
    digitalWrite(MOTOR_PIN_D6, state);
    digitalWrite(MOTOR_PIN_D7, state);
    digitalWrite(MOTOR_PIN_D8, state);
    
    // 状態LED
    digitalWrite(PIN_LED, state ? HIGH : LOW);
}

void stopMotors() {
    currentMotorMode = MOTOR_OFF;
    currentModeName = "OFF";
    patternStep = 0;
    lastPatternTime = 0;
    setAllMotors(LOW);
    Serial.println("Motor2 stopped");
}

// ステータス送信
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[4] = {
            0x02,  // Motor2識別子
            (uint8_t)currentMotorMode,
            (uint8_t)(patternStep > 0 ? 1 : 0),
            0x00
        };
        pStatusChar->setValue(status, 4);
        pStatusChar->notify();
    }
}

// === 文字列コマンド処理（MQTT版互換）===

void processStringCommand(const String& cmd) {
    Serial.printf("Motor2 received: %s\n", cmd.c_str());
    
    // コマンドをカンマで分割
    int comma = cmd.indexOf(',');
    String cmdType = (comma > 0) ? cmd.substring(0, comma) : cmd;
    String mode = (comma > 0) ? cmd.substring(comma + 1) : "";
    cmdType.trim();
    cmdType.toUpperCase();
    mode.trim();
    mode.toUpperCase();
    
    if (cmdType == "MOTOR" || cmdType == "VIB" || cmdType == "VIBRATION") {
        // モード切替時は、一旦ステートをリセット
        patternStep = 0;
        lastPatternTime = 0;
        
        if (mode == "OFF" || mode == "") {
            stopMotors();
        }
        else if (mode == "STRONG") {
            currentMotorMode = MOTOR_STRONG;
            currentModeName = mode;
        }
        else if (mode == "MEDIUM_STRONG") {
            currentMotorMode = MOTOR_MEDIUM_STRONG;
            currentModeName = mode;
        }
        else if (mode == "MEDIUM_WEAK") {
            currentMotorMode = MOTOR_MEDIUM_WEAK;
            currentModeName = mode;
        }
        else if (mode == "WEAK") {
            currentMotorMode = MOTOR_WEAK;
            currentModeName = mode;
        }
        else if (mode == "HEARTBEAT") {
            currentMotorMode = MOTOR_HEARTBEAT;
            currentModeName = mode;
        }
        else if (mode == "RUMBLE_FAST") {
            currentMotorMode = MOTOR_RUMBLE_FAST;
            currentModeName = mode;
        }
        else if (mode == "RUMBLE_SLOW") {
            currentMotorMode = MOTOR_RUMBLE_SLOW;
            currentModeName = mode;
        }
        else {
            Serial.printf("Unknown mode: %s\n", mode.c_str());
            return;
        }
        
        Serial.printf("Motor2 mode set: %s\n", currentModeName.c_str());
    }
    else if (cmdType == "OFF" || cmdType == "STOP") {
        stopMotors();
    }
    else {
        Serial.printf("Unknown command: %s\n", cmdType.c_str());
    }
    
    sendStatus();
}

/**
 * @brief モーター制御のメイン関数（MQTT版と同一ロジック）
 */
void handleMotors(unsigned long now) {
    
    switch (currentMotorMode) {
        
        case MOTOR_OFF:
            setAllMotors(LOW);
            break;
            
        case MOTOR_WEAK:
            // 「振動弱はD8のモーターを動かす」
            digitalWrite(MOTOR_PIN_D5, LOW);
            digitalWrite(MOTOR_PIN_D6, LOW);
            digitalWrite(MOTOR_PIN_D7, LOW);
            digitalWrite(MOTOR_PIN_D8, HIGH);
            digitalWrite(PIN_LED, HIGH);
            break;
            
        case MOTOR_MEDIUM_WEAK:
            // 「振動中弱はD7のモーターを動かす」
            digitalWrite(MOTOR_PIN_D5, LOW);
            digitalWrite(MOTOR_PIN_D6, LOW);
            digitalWrite(MOTOR_PIN_D7, HIGH);
            digitalWrite(MOTOR_PIN_D8, LOW);
            digitalWrite(PIN_LED, HIGH);
            break;
            
        case MOTOR_MEDIUM_STRONG:
            // 「振動中強はD6とD7のモーターを動かす」
            digitalWrite(MOTOR_PIN_D5, LOW);
            digitalWrite(MOTOR_PIN_D6, HIGH);
            digitalWrite(MOTOR_PIN_D7, HIGH);
            digitalWrite(MOTOR_PIN_D8, LOW);
            digitalWrite(PIN_LED, HIGH);
            break;
            
        case MOTOR_STRONG:
            // 「振動強は全部のモーターを回す」
            setAllMotors(HIGH);
            break;

        // ----- ノンブロッキング・パターン -----
        
        case MOTOR_HEARTBEAT:
            // 心拍 (ドッ..クン.......ドッ..クン...)
            // ドッ = 中弱 (D7), クン = 強 (D5)
            // ステップ0: (1.5秒待機) ドッ (中弱 D7)
            if (patternStep == 0 && (now - lastPatternTime > 1500)) { 
                setAllMotors(LOW);
                digitalWrite(MOTOR_PIN_D7, HIGH);
                digitalWrite(PIN_LED, HIGH);
                lastPatternTime = now;
                patternStep = 1;
            }
            // ステップ1: (200ms) OFF
            else if (patternStep == 1 && (now - lastPatternTime > 200)) { 
                setAllMotors(LOW);
                lastPatternTime = now;
                patternStep = 2;
            }
            // ステップ2: (100ms) クン (強 D5)
            else if (patternStep == 2 && (now - lastPatternTime > 100)) { 
                digitalWrite(MOTOR_PIN_D5, HIGH);
                digitalWrite(PIN_LED, HIGH);
                lastPatternTime = now;
                patternStep = 3;
            }
            // ステップ3: (150ms) OFF
            else if (patternStep == 3 && (now - lastPatternTime > 150)) { 
                setAllMotors(LOW);
                lastPatternTime = now;
                patternStep = 0; // ループ
            }
            break;
            
        case MOTOR_RUMBLE_FAST:
            // ドンドンドン (速)
            // ステップ0: (0.15秒待機) ドン (全モーター)
            if (patternStep == 0 && (now - lastPatternTime > 150)) {
                setAllMotors(HIGH);
                lastPatternTime = now;
                patternStep = 1;
            }
            // ステップ1: (100ms) OFF
            else if (patternStep == 1 && (now - lastPatternTime > 100)) {
                setAllMotors(LOW);
                lastPatternTime = now;
                patternStep = 0; // ループ
            }
            break;
            
        case MOTOR_RUMBLE_SLOW:
            // ドン...ドン... (遅)
            // ステップ0: (0.3秒待機) ドン (全モーター)
            if (patternStep == 0 && (now - lastPatternTime > 300)) {
                setAllMotors(HIGH);
                lastPatternTime = now;
                patternStep = 1;
            }
            // ステップ1: (300ms) OFF
            else if (patternStep == 1 && (now - lastPatternTime > 300)) {
                setAllMotors(LOW);
                lastPatternTime = now;
                patternStep = 0; // ループ
            }
            break;
    }
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
        stopMotors();
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
    Serial.println("4D@HOME ActionDrive Motor2 starting...");
    Serial.println("MQTT compatible - 4-pin digital control");
    
    // GPIO設定（4ピン構成）
    pinMode(MOTOR_PIN_D5, OUTPUT);
    pinMode(MOTOR_PIN_D6, OUTPUT);
    pinMode(MOTOR_PIN_D7, OUTPUT);
    pinMode(MOTOR_PIN_D8, OUTPUT);
    pinMode(PIN_LED, OUTPUT);
    
    // 初期状態: 全OFF
    setAllMotors(LOW);
    
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
    pAdvertising->setMaxPreferred(0x12);
    BLEDevice::startAdvertising();
    
    Serial.println("BLE advertising started");
    Serial.printf("Pins: D5=%d, D6=%d, D7=%d, D8=%d\n", 
                  MOTOR_PIN_D5, MOTOR_PIN_D6, MOTOR_PIN_D7, MOTOR_PIN_D8);
    
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
    unsigned long now = millis();
    
    // ノンブロッキング モーター制御
    handleMotors(now);
    
    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        BLEDevice::startAdvertising();
        Serial.println("Advertising restarted");
        oldDeviceConnected = deviceConnected;
    }
    
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }
    
    delay(10);
}
