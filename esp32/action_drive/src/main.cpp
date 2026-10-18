/**
 * 4D@HOME ActionDrive ESP32 ファームウェア（1台で背中＋お尻を制御）
 *
 * 以前は Motor1（背中）と Motor2（お尻）に ESP32 を1台ずつ使っていたが、
 * 1台の ESP32 で両方の4ピンを独立に制御する。
 *
 * コマンド形式:
 *   "MOTOR1,mode"  背中（Motor1）
 *   "MOTOR2,mode"  お尻（Motor2）
 *   "MOTOR,mode"   両方（VIB / VIBRATION も同じ）
 *   "OFF" / "STOP" 全停止
 * mode: STRONG, MEDIUM_STRONG, MEDIUM_WEAK, WEAK,
 *       HEARTBEAT, RUMBLE_FAST, RUMBLE_SLOW, OFF
 *
 * ピン構成（駆動は 2SC2334 + ベース抵抗 150Ω + B-E間 10kΩ）:
 *   背中 Motor1 : 強=GPIO25, 中強=GPIO26, 中弱=GPIO27, 弱=GPIO32（コネクタとケーブルで背中基板へ）
 *   お尻 Motor2 : 強=GPIO23, 中強=GPIO22, 中弱=GPIO21, 弱=GPIO19（150Ω と 10kΩ を ESP32 の下に寝かせた側）
 *   GPIO15（起動時に信号が出る）と GPIO16/17（内部フラッシュと同じ電源系統）は使わない
 *
 * 8個のモーターが同時に回り始めると電源が一瞬落ち込むので、
 * ON にするピンは STAGGER_MS ずつずらして1本ずつ切り替える（OFF はすぐ）。
 *
 * ビルド: PlatformIO（platformio.ini、Arduino-ESP32 2.x）でも
 *         arduino-cli（action_drive.ino、Arduino-ESP32 3.x）でも通る。
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <driver/gpio.h>
#include <esp_mac.h>   // esp_read_mac（3.x ではここに移った。2.x にもある）

// === ピン定義 ===
// 並びは {強(旧D5), 中強(旧D6), 中弱(旧D7), 弱(旧D8)}
static const uint8_t BACK_PINS[4] = {25, 26, 27, 32};   // 背中 Motor1
static const uint8_t SEAT_PINS[4] = {23, 22, 21, 19};   // お尻 Motor2
#define PIN_LED       2     // 状態表示LED (オンボード)
#define STAGGER_MS    3     // ON にするピンをずらす間隔

// ピン配列の添字
#define IDX_D5  0   // 振動 強 (STRONG)
#define IDX_D6  1   // 振動 中強 (MEDIUM_STRONG)
#define IDX_D7  2   // 振動 中強/中弱 (MEDIUM_STRONG/MEDIUM_WEAK)
#define IDX_D8  3   // 振動 弱 (WEAK)

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

// 背中・お尻それぞれの状態（パターンも別々に進む）
struct MotorGroup {
    const char* label;
    const uint8_t* pins;
    MotorMode mode;
    String modeName;
    unsigned long lastPatternTime;
    int patternStep;
    uint8_t wantBits;  // 出したいピンの状態
    uint8_t outBits;   // 実際に出力しているピンの状態
};

MotorGroup backGroup = {"Motor1(背中)", BACK_PINS, MOTOR_OFF, "OFF", 0, 0, 0, 0};
MotorGroup seatGroup = {"Motor2(お尻)", SEAT_PINS, MOTOR_OFF, "OFF", 0, 0, 0, 0};

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;

volatile bool deviceConnected = false;
bool oldDeviceConnected = false;

// BLE のコールバックは別タスクで動くので、コマンドはキューに入れて loop() で処理する
#define CMD_MAX_LEN 48
QueueHandle_t commandQueue = nullptr;
volatile bool stopRequested = false;  // 切断時の全停止要求

// デバイス名生成
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    sprintf(name, "4D_AD_%02X%02X", mac[4], mac[5]);
    return String(name);
}

// === モーター制御ヘルパー ===

// 出したい状態を決めるだけ。実際の出力は applyOutputs() が行う
void writePins(MotorGroup& g, bool d5, bool d6, bool d7, bool d8) {
    g.wantBits = (d5 << IDX_D5) | (d6 << IDX_D6) | (d7 << IDX_D7) | (d8 << IDX_D8);
}

/**
 * @brief 出したい状態を実際のピンへ反映する
 * OFF はすぐ切る。ON は前回 ON にしてから STAGGER_MS 経ったら1本だけ切り替える
 */
unsigned long lastSwitchOnTime = 0;

void applyOutputs(unsigned long now) {
    MotorGroup* groups[2] = {&backGroup, &seatGroup};
    for (MotorGroup* g : groups) {
        for (int i = 0; i < 4; i++) {
            uint8_t bit = 1 << i;
            if (!(g->wantBits & bit) && (g->outBits & bit)) {
                digitalWrite(g->pins[i], LOW);
                g->outBits &= ~bit;
            }
        }
    }
    if (now - lastSwitchOnTime < STAGGER_MS) return;
    for (MotorGroup* g : groups) {
        for (int i = 0; i < 4; i++) {
            uint8_t bit = 1 << i;
            if ((g->wantBits & bit) && !(g->outBits & bit)) {
                digitalWrite(g->pins[i], HIGH);
                g->outBits |= bit;
                lastSwitchOnTime = now;
                return;
            }
        }
    }
}

void setGroupAll(MotorGroup& g, bool state) {
    writePins(g, state, state, state, state);
}

// どちらかのモーターが回っていれば状態LEDを点ける
void updateLed() {
    bool on = (backGroup.outBits | seatGroup.outBits) != 0;
    digitalWrite(PIN_LED, on ? HIGH : LOW);
}

void stopGroup(MotorGroup& g) {
    g.mode = MOTOR_OFF;
    g.modeName = "OFF";
    g.patternStep = 0;
    g.lastPatternTime = 0;
    setGroupAll(g, LOW);
}

void stopAllMotors() {
    stopGroup(backGroup);
    stopGroup(seatGroup);
    applyOutputs(millis());   // OFF はすぐ反映される
    digitalWrite(PIN_LED, LOW);
    Serial.println("All motors stopped");
}

// ステータス送信
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[4] = {
            0x03,  // 1台型（背中＋お尻）識別子
            (uint8_t)backGroup.mode,
            (uint8_t)seatGroup.mode,
            0x00
        };
        pStatusChar->setValue(status, 4);
        pStatusChar->notify();
    }
}

// === 文字列コマンド処理 ===

bool parseMode(const String& mode, MotorMode& out) {
    if (mode == "OFF" || mode == "")    { out = MOTOR_OFF; return true; }
    if (mode == "STRONG")               { out = MOTOR_STRONG; return true; }
    if (mode == "MEDIUM_STRONG")        { out = MOTOR_MEDIUM_STRONG; return true; }
    if (mode == "MEDIUM_WEAK")          { out = MOTOR_MEDIUM_WEAK; return true; }
    if (mode == "WEAK")                 { out = MOTOR_WEAK; return true; }
    if (mode == "HEARTBEAT")            { out = MOTOR_HEARTBEAT; return true; }
    if (mode == "RUMBLE_FAST")          { out = MOTOR_RUMBLE_FAST; return true; }
    if (mode == "RUMBLE_SLOW")          { out = MOTOR_RUMBLE_SLOW; return true; }
    return false;
}

void applyMode(MotorGroup& g, MotorMode mode, const String& modeName) {
    // 同じモードが送り直されたときは、パターンを途中から崩さない
    if (mode == g.mode) return;
    // モード切替時は、一旦ステートをリセット
    g.patternStep = 0;
    g.lastPatternTime = 0;
    if (mode == MOTOR_OFF) {
        stopGroup(g);
    } else {
        g.mode = mode;
        g.modeName = modeName;
    }
    Serial.printf("%s mode set: %s\n", g.label, g.modeName.c_str());
}

void processStringCommand(const String& cmd) {
    Serial.printf("Received: %s\n", cmd.c_str());

    // 切断後に届いていたコマンドでモーターを回さない
    if (!deviceConnected) return;

    // コマンドをカンマで分割
    int comma = cmd.indexOf(',');
    String cmdType = (comma > 0) ? cmd.substring(0, comma) : cmd;
    String mode = (comma > 0) ? cmd.substring(comma + 1) : "";
    cmdType.trim();
    cmdType.toUpperCase();
    mode.trim();
    mode.toUpperCase();

    if (cmdType == "OFF" || cmdType == "STOP") {
        stopAllMotors();
        sendStatus();
        return;
    }

    bool toBack = false;
    bool toSeat = false;
    if (cmdType == "MOTOR1") {
        toBack = true;
    } else if (cmdType == "MOTOR2") {
        toSeat = true;
    } else if (cmdType == "MOTOR" || cmdType == "VIB" || cmdType == "VIBRATION") {
        toBack = true;
        toSeat = true;
    } else {
        Serial.printf("Unknown command: %s\n", cmdType.c_str());
        return;
    }

    MotorMode parsed;
    if (!parseMode(mode, parsed)) {
        Serial.printf("Unknown mode: %s\n", mode.c_str());
        return;
    }

    if (toBack) applyMode(backGroup, parsed, mode);
    if (toSeat) applyMode(seatGroup, parsed, mode);
    updateLed();
    sendStatus();
}

/**
 * @brief 1グループ分のモーター制御（MQTT版と同一ロジック）
 */
void handleGroup(MotorGroup& g, unsigned long now) {

    switch (g.mode) {

        case MOTOR_OFF:
            setGroupAll(g, LOW);
            break;

        case MOTOR_WEAK:
            // 「振動弱はD8のモーターを動かす」
            writePins(g, LOW, LOW, LOW, HIGH);
            break;

        case MOTOR_MEDIUM_WEAK:
            // 「振動中弱はD7のモーターを動かす」
            writePins(g, LOW, LOW, HIGH, LOW);
            break;

        case MOTOR_MEDIUM_STRONG:
            // 「振動中強はD6とD7のモーターを動かす」
            writePins(g, LOW, HIGH, HIGH, LOW);
            break;

        case MOTOR_STRONG:
            // 「振動強は全部のモーターを回す」
            setGroupAll(g, HIGH);
            break;

        // ----- ノンブロッキング・パターン -----

        case MOTOR_HEARTBEAT:
            // 心拍 (ドッ..クン.......ドッ..クン...)
            // ドッ = 中弱 (D7), クン = 強 (D5)
            // ステップ0: (1.5秒待機) ドッ (中弱 D7)
            if (g.patternStep == 0 && (now - g.lastPatternTime > 1500)) {
                writePins(g, LOW, LOW, HIGH, LOW);
                g.lastPatternTime = now;
                g.patternStep = 1;
            }
            // ステップ1: (200ms) OFF
            else if (g.patternStep == 1 && (now - g.lastPatternTime > 200)) {
                setGroupAll(g, LOW);
                g.lastPatternTime = now;
                g.patternStep = 2;
            }
            // ステップ2: (100ms) クン (強 D5)
            else if (g.patternStep == 2 && (now - g.lastPatternTime > 100)) {
                writePins(g, HIGH, LOW, LOW, LOW);
                g.lastPatternTime = now;
                g.patternStep = 3;
            }
            // ステップ3: (150ms) OFF
            else if (g.patternStep == 3 && (now - g.lastPatternTime > 150)) {
                setGroupAll(g, LOW);
                g.lastPatternTime = now;
                g.patternStep = 0; // ループ
            }
            break;

        case MOTOR_RUMBLE_FAST:
            // ドンドンドン (速)
            // ステップ0: (0.15秒待機) ドン (全モーター)
            if (g.patternStep == 0 && (now - g.lastPatternTime > 150)) {
                setGroupAll(g, HIGH);
                g.lastPatternTime = now;
                g.patternStep = 1;
            }
            // ステップ1: (100ms) OFF
            else if (g.patternStep == 1 && (now - g.lastPatternTime > 100)) {
                setGroupAll(g, LOW);
                g.lastPatternTime = now;
                g.patternStep = 0; // ループ
            }
            break;

        case MOTOR_RUMBLE_SLOW:
            // ドン...ドン... (遅)
            // ステップ0: (0.3秒待機) ドン (全モーター)
            if (g.patternStep == 0 && (now - g.lastPatternTime > 300)) {
                setGroupAll(g, HIGH);
                g.lastPatternTime = now;
                g.patternStep = 1;
            }
            // ステップ1: (300ms) OFF
            else if (g.patternStep == 1 && (now - g.lastPatternTime > 300)) {
                setGroupAll(g, LOW);
                g.lastPatternTime = now;
                g.patternStep = 0; // ループ
            }
            break;
    }
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

        // 安全のためモーター停止（実際の停止は loop() で行う）
        stopRequested = true;
    }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) {
        // getValue() は 2.x では std::string、3.x では String を返すので、c_str() だけを使う
        auto value = pCharacteristic->getValue();
        if (value.length() == 0 || commandQueue == nullptr) return;

        char buf[CMD_MAX_LEN] = {0};
        strncpy(buf, value.c_str(), CMD_MAX_LEN - 1);
        if (xQueueSend(commandQueue, buf, 0) != pdTRUE) {
            Serial.println("Command queue full");
        }
    }
};

// === セットアップ ===

void setupPins(const uint8_t* pins) {
    for (int i = 0; i < 4; i++) {
        pinMode(pins[i], OUTPUT);
        digitalWrite(pins[i], LOW);
        // ベース電流 約11mA を安定して流すため駆動能力を最大にする
        gpio_set_drive_capability((gpio_num_t)pins[i], GPIO_DRIVE_CAP_3);
    }
}

void setup() {
    Serial.begin(115200);
    Serial.println("4D@HOME ActionDrive (back + seat) starting...");

    // GPIO設定（背中4ピン＋お尻4ピン）
    setupPins(BACK_PINS);
    setupPins(SEAT_PINS);
    pinMode(PIN_LED, OUTPUT);
    digitalWrite(PIN_LED, LOW);

    commandQueue = xQueueCreate(16, CMD_MAX_LEN);

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
    // 広告パケットに Service UUID、スキャン応答にデバイス名を載せる
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();

    BLEAdvertisementData advertisementData;
    advertisementData.setFlags(0x06);  // LE General Discoverable + BR/EDR Not Supported
    advertisementData.setCompleteServices(BLEUUID(SERVICE_UUID));

    BLEAdvertisementData scanResponseData;
    scanResponseData.setName(deviceName.c_str());

    pAdvertising->setAdvertisementData(advertisementData);
    pAdvertising->setScanResponseData(scanResponseData);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMaxPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println("BLE advertising started");
    Serial.printf("Back pins: %d,%d,%d,%d  Seat pins: %d,%d,%d,%d\n",
                  BACK_PINS[0], BACK_PINS[1], BACK_PINS[2], BACK_PINS[3],
                  SEAT_PINS[0], SEAT_PINS[1], SEAT_PINS[2], SEAT_PINS[3]);

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

    // 受信したコマンドを処理
    char buf[CMD_MAX_LEN];
    while (xQueueReceive(commandQueue, buf, 0) == pdTRUE) {
        processStringCommand(String(buf));
    }

    // 切断時の全停止はコマンド処理の後に行い、残っていたコマンドも捨てる
    if (stopRequested) {
        stopRequested = false;
        xQueueReset(commandQueue);
        stopAllMotors();
    }

    // ノンブロッキング モーター制御（背中とお尻は独立）
    handleGroup(backGroup, now);
    handleGroup(seatGroup, now);
    applyOutputs(now);
    updateLed();

    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        BLEDevice::startAdvertising();
        Serial.println("Advertising restarted");
        oldDeviceConnected = deviceConnected;
    }

    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
        // 接続時LED点滅
        for (int i = 0; i < 2; i++) {
            digitalWrite(PIN_LED, HIGH);
            delay(100);
            digitalWrite(PIN_LED, LOW);
            delay(100);
        }
    }

    delay(1);
}
