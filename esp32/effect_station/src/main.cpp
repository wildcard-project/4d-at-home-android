/**
 * 4D@HOME EffectStation ESP32 ファームウェア
 * 4DHOME_STATION_CONTROL.ino準拠の実装（BLE対応版）
 * 
 * BLE経由でコマンドを受信し、以下のエフェクトを制御:
 * - FAN (ファン): "FAN,0" / "FAN,1"
 * - SPLASH (水噴射): "SPLASH"
 * - MIST (ミスト): "MIST,0" / "MIST,1" / "MIST,2"
 * - LED (NeoPixel RGBW): "LED,colorId,brightness,effect,transition"
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Adafruit_NeoPixel.h>

// ==========================================
// 1. ピン定義 (Pin Definitions)
// ==========================================
#define PIN_FAN       25    // 風 (ON/OFF)
#define PIN_SPLASH    26    // 水しぶき (一瞬)
#define PIN_LED       27    // LED (RGBW NeoPixel)
#define PIN_MIST      32    // ミスト (NPNトランジスタ経由)
#define NUM_LEDS      1     // LEDの数（元の仕様に合わせる）

// === BLE UUIDs ===
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// ==========================================
// 2. 状態管理変数 (State Variables)
// ==========================================

// === グローバル変数 ===
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandChar = nullptr;
BLECharacteristic* pStatusChar = nullptr;

// LED初期化 (RGBW, 800kHz) - 元の仕様に合わせる
Adafruit_NeoPixel strip(NUM_LEDS, PIN_LED, NEO_RGBW + NEO_KHZ800);

// --- 色テーブル (RGBW形式) ---
uint32_t colors[12];

bool deviceConnected = false;
bool oldDeviceConnected = false;

// --- 水しぶき (Splash) ---
unsigned long splashStartTime = 0;
bool splashActive = false;
const int SPLASH_DURATION = 200;  // 水が出る時間(ms)

// --- ミスト (Mist) ---
// ミスト基盤は「1回押すとON、もう一度押すとOFF」のトグル動作
bool isMistOn = false;      // ESP32が把握しているミストの状態
int mistAutoOffMode = 0;    // 0:なし, 1:自動OFF待ち
unsigned long mistStartTime = 0;
const int MIST_DURATION = 100;  // 一瞬モードの時間(ms) - 元の仕様

// --- LED ---
int ledTargetColorIdx = 11;  // 目標の色ID
int ledBrightnessMode = 0;   // 0:なし, 1:弱, 2:強
int ledEffect = 0;           // 0:点灯, 1:点滅, 2:呼吸
int ledTransition = 0;       // 0:一瞬, 1:フェード

unsigned long lastLedUpdate = 0;
// 現在の色の値 (フェード計算用)
float currentR = 0, currentG = 0, currentB = 0, currentW = 0;

// 現在のエフェクト状態（BLEステータス通知用）
struct EffectState {
    bool fanOn = false;
    bool splashActive = false;
    int mistMode = 0;
    uint8_t colorId = 11;
    uint8_t brightness = 0;
    int ledEffect = 0;
} currentState;

// ==========================================
// 3. 色定義 (Color Setup)
// ==========================================
void setupColors() {
    // フォーマット: strip.Color(R, G, B, W) - 元の仕様に合わせる
    colors[0]  = strip.Color(255, 20, 100, 0);   // 0: ピンク (PINK)
    colors[1]  = strip.Color(255, 0, 0, 0);      // 1: 赤 (RED)
    colors[2]  = strip.Color(255, 100, 0, 0);    // 2: オレンジ (ORANGE)
    colors[3]  = strip.Color(255, 255, 0, 0);    // 3: 黄色 (YELLOW)
    colors[4]  = strip.Color(150, 255, 0, 0);    // 4: 黄緑 (YELLOW_GREEN)
    colors[5]  = strip.Color(0, 255, 0, 0);      // 5: 緑 (GREEN)
    colors[6]  = strip.Color(0, 100, 0, 0);      // 6: 深緑 (DARK_GREEN)
    colors[7]  = strip.Color(0, 255, 255, 0);    // 7: 水色 (CYAN)
    colors[8]  = strip.Color(0, 0, 255, 0);      // 8: 青 (BLUE)
    colors[9]  = strip.Color(150, 0, 255, 0);    // 9: 紫 (PURPLE)
    colors[10] = strip.Color(0, 0, 0, 255);      // 10: 白 (WHITE - Wチャンネルのみ)
    colors[11] = strip.Color(0, 0, 0, 0);        // 11: なし (OFF)
}

// デバイス名生成
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    sprintf(name, "4D_ES_%02X%02X", mac[4], mac[5]);
    return String(name);
}

// ==========================================
// 4. ミストボタン制御 (Click Simulation)
// ==========================================
// タクトスイッチを一回「カチッ」と押す動作を再現
void clickMistButton() {
    digitalWrite(PIN_MIST, HIGH);  // 押す
    delay(100);                    // 0.1秒キープ
    digitalWrite(PIN_MIST, LOW);   // 離す
    delay(100);                    // 連続入力防止
}

// ==========================================
// 5. エフェクト制御関数
// ==========================================

void setFan(bool on) {
    currentState.fanOn = on;
    digitalWrite(PIN_FAN, on ? HIGH : LOW);
    Serial.printf("FAN: %s\n", on ? "ON" : "OFF");
}

void triggerSplash() {
    splashActive = true;
    splashStartTime = millis();
    digitalWrite(PIN_SPLASH, HIGH);
    currentState.splashActive = true;
    Serial.println("SPLASH triggered");
}

void updateSplash() {
    if (splashActive) {
        if (millis() - splashStartTime >= SPLASH_DURATION) {
            digitalWrite(PIN_SPLASH, LOW);
            splashActive = false;
            currentState.splashActive = false;
        }
    }
}

void setMist(int reqMode) {
    currentState.mistMode = reqMode;
    
    if (reqMode == 1) {  // 一瞬モード
        if (!isMistOn) {
            clickMistButton();  // OFFならONにする
            isMistOn = true;
        }
        mistAutoOffMode = 1;  // タイマーセット
        mistStartTime = millis();
        Serial.println("MIST: SHOT (Auto OFF set)");
    }
    else if (reqMode == 2) {  // 継続モード
        mistAutoOffMode = 0;  // タイマー解除
        if (!isMistOn) {
            clickMistButton();  // OFFならONにする
            isMistOn = true;
            Serial.println("MIST: ON (Continuous)");
        } else {
            Serial.println("MIST: already ON");
        }
    }
    else {  // OFFモード
        mistAutoOffMode = 0;
        if (isMistOn) {
            clickMistButton();  // ONならOFFにする
            isMistOn = false;
            Serial.println("MIST: OFF");
        } else {
            Serial.println("MIST: already OFF");
        }
    }
}

void updateMist() {
    if (mistAutoOffMode == 1) {
        if (millis() - mistStartTime >= MIST_DURATION) {
            // 時間が来たらOFFにする
            if (isMistOn) {
                clickMistButton();
                isMistOn = false;
                currentState.mistMode = 0;
                Serial.println("AUTO: Mist Timer OFF");
            }
            mistAutoOffMode = 0;
        }
    }
}

void setLedColor(uint8_t colorId, uint8_t brightnessLevel, int effect, int transition) {
    if (colorId >= 12) colorId = 11;  // 無効なIDはOFF
    if (brightnessLevel >= 3) brightnessLevel = 2;
    
    ledTargetColorIdx = colorId;
    ledBrightnessMode = brightnessLevel;
    ledEffect = effect;
    ledTransition = transition;
    
    currentState.colorId = colorId;
    currentState.brightness = brightnessLevel;
    currentState.ledEffect = effect;
    
    // 「一瞬切り替え」なら、現在値を即座に目標値にする
    if (ledTransition == 0) {
        uint32_t c = colors[ledTargetColorIdx];
        // 強さが0(なし)なら黒にする
        if (ledBrightnessMode == 0) c = colors[11];
        
        currentR = (uint8_t)(c >> 16);
        currentG = (uint8_t)(c >> 8);
        currentB = (uint8_t)(c);
        currentW = (uint8_t)(c >> 24);
    }
    
    Serial.printf("LED: colorId=%d, brightness=%d, effect=%d, transition=%d\n",
                  colorId, brightnessLevel, effect, transition);
}

void updateLED() {
    unsigned long now = millis();
    // 30msごとに更新 (滑らかなアニメーションのため)
    if (now - lastLedUpdate < 30) return;
    lastLedUpdate = now;
    
    // 目標色の取得
    uint32_t targetColorRaw = colors[ledTargetColorIdx];
    if (ledBrightnessMode == 0) targetColorRaw = colors[11];  // 強さ0なら消灯
    
    float tR = (uint8_t)(targetColorRaw >> 16);
    float tG = (uint8_t)(targetColorRaw >> 8);
    float tB = (uint8_t)(targetColorRaw);
    float tW = (uint8_t)(targetColorRaw >> 24);
    
    // フェード処理 (Transition)
    if (ledTransition == 1) {
        float step = 10.0;  // 変化のスピード
        if (currentR < tR) currentR += step; else if (currentR > tR) currentR -= step;
        if (currentG < tG) currentG += step; else if (currentG > tG) currentG -= step;
        if (currentB < tB) currentB += step; else if (currentB > tB) currentB -= step;
        if (currentW < tW) currentW += step; else if (currentW > tW) currentW -= step;
        
        // 行き過ぎ補正
        if (abs(currentR - tR) < step) currentR = tR;
        if (abs(currentG - tG) < step) currentG = tG;
        if (abs(currentB - tB) < step) currentB = tB;
        if (abs(currentW - tW) < step) currentW = tW;
    } else {
        // 一瞬切り替えの場合はすでにsetLedColorでセット済みだが念のため
        currentR = tR; currentG = tG; currentB = tB; currentW = tW;
    }
    
    // エフェクト計算 (点滅・呼吸)
    float brightnessFactor = 1.0;
    
    // 強さ係数
    if (ledBrightnessMode == 1) brightnessFactor = 0.2;       // 弱
    else if (ledBrightnessMode == 2) brightnessFactor = 1.0;  // 強
    else brightnessFactor = 0.0;                              // なし
    
    // エフェクト
    if (ledEffect == 1) {  // 点滅 (Blink)
        if ((now / 250) % 2 == 0) brightnessFactor = 0;
    }
    else if (ledEffect == 2) {  // 呼吸 (Breathe)
        float val = (sin(now / 300.0) + 1.0) / 2.0;
        brightnessFactor *= val;
    }
    
    // 最終出力
    uint8_t finalR = (uint8_t)(currentR * brightnessFactor);
    uint8_t finalG = (uint8_t)(currentG * brightnessFactor);
    uint8_t finalB = (uint8_t)(currentB * brightnessFactor);
    uint8_t finalW = (uint8_t)(currentW * brightnessFactor);
    
    strip.setPixelColor(0, strip.Color(finalR, finalG, finalB, finalW));
    strip.show();
}

void allOff() {
    setFan(false);
    setMist(0);
    setLedColor(11, 0, 0, 0);
    Serial.println("All effects OFF");
}

// ==========================================
// 6. ステータス送信
// ==========================================
void sendStatus() {
    if (deviceConnected && pStatusChar != nullptr) {
        uint8_t status[8] = {
            (uint8_t)(currentState.fanOn ? 1 : 0),
            (uint8_t)(currentState.splashActive ? 1 : 0),
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

// ==========================================
// 7. 文字列コマンド処理（BLE経由）
// ==========================================
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

// ==========================================
// 8. BLEコールバック
// ==========================================
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
        std::string stdValue = pCharacteristic->getValue();
        String value = String(stdValue.c_str());
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

// ==========================================
// 9. セットアップ
// ==========================================
void setup() {
    Serial.begin(115200);
    Serial.println("===================================");
    Serial.println(" 4DX@HOME EffectStation - BLE Mode");
    Serial.println("===================================");
    
    // ピン設定（元の仕様に合わせてdigitalWrite制御）
    pinMode(PIN_FAN, OUTPUT);
    pinMode(PIN_SPLASH, OUTPUT);
    pinMode(PIN_MIST, OUTPUT);
    
    // 初期化 (すべてOFF)
    digitalWrite(PIN_FAN, LOW);
    digitalWrite(PIN_SPLASH, LOW);
    digitalWrite(PIN_MIST, LOW);  // ボタンは離した状態
    
    // NeoPixel初期化
    strip.begin();
    strip.show();
    setupColors();  // 色定義の読み込み
    
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
        setLedColor(5, 2, 0, 0);  // GREEN, 強
        delay(200);
        setLedColor(11, 0, 0, 0);  // OFF
        delay(200);
    }
    
    Serial.println("Ready for commands");
}

// ==========================================
// 10. メインループ
// ==========================================
void loop() {
    // 各機能の自動処理 (時間管理)
    updateSplash();
    updateMist();
    updateLED();
    
    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);  // Bluetoothスタックに時間を与える
        BLEDevice::startAdvertising();
        Serial.println("Advertising restarted");
        oldDeviceConnected = deviceConnected;
    }
    
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }
    
    delay(10);
}