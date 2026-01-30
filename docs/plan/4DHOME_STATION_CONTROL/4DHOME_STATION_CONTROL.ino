#include <Adafruit_NeoPixel.h>

// ==========================================
// 1. ピン定義 (Pin Definitions)
// ==========================================
const int PIN_FAN    = 25;  // 風 (ON/OFF)
const int PIN_SPLASH = 26;  // 水しぶき (一瞬)
const int PIN_LED    = 27;  // LED (RGBW NeoPixel)
const int PIN_MIST   = 32;  // ミスト (NPNトランジスタ経由)

const int NUM_LEDS   = 1;   // LEDの数

// LED初期化 (RGBW, 800kHz)
Adafruit_NeoPixel strip(NUM_LEDS, PIN_LED, NEO_RGBW + NEO_KHZ800);

// ==========================================
// 2. 状態管理変数 (State Variables)
// ==========================================

// --- 色テーブル ---
uint32_t colors[12]; 

// --- 水しぶき (Splash) ---
unsigned long splashStartTime = 0;
bool splashActive = false;
const int SPLASH_DURATION = 200; // 水が出る時間(ms)

// --- ミスト (Mist) ---
// ミスト基盤は「1回押すとON、もう一度押すとOFF」のトグル動作
bool isMistOn = false;      // ESP32が把握しているミストの状態
int mistAutoOffMode = 0;    // 0:なし, 1:自動OFF待ち
unsigned long mistStartTime = 0;
const int MIST_DURATION = 100; // 一瞬モードの時間(ms)

// --- LED ---
int ledTargetColorIdx = 11; // 目標の色ID
int ledBrightnessMode = 0;  // 0:なし, 1:弱, 2:強
int ledEffect = 0;          // 0:点灯, 1:点滅, 2:呼吸
int ledTransition = 0;      // 0:一瞬, 1:フェード

unsigned long lastLedUpdate = 0;
// 現在の色の値 (フェード計算用)
float currentR = 0, currentG = 0, currentB = 0, currentW = 0;

// ==========================================
// 3. 初期設定 (Setup)
// ==========================================
void setup() {
  Serial.begin(115200);
  
  // ピン設定
  pinMode(PIN_FAN, OUTPUT);
  pinMode(PIN_SPLASH, OUTPUT);
  pinMode(PIN_MIST, OUTPUT);
  
  // 初期化 (すべてOFF)
  digitalWrite(PIN_FAN, LOW);
  digitalWrite(PIN_SPLASH, LOW);
  digitalWrite(PIN_MIST, LOW); // ボタンは離した状態

  // LED初期化
  strip.begin();
  strip.show();
  setupColors(); // 色定義の読み込み

  Serial.println("===================================");
  Serial.println(" 4DX@HOME Controller - READY");
  Serial.println("===================================");
}

// ==========================================
// 4. メインループ (Main Loop)
// ==========================================
void loop() {
  // シリアルコマンド受信
  if (Serial.available() > 0) {
    String input = Serial.readStringUntil('\n');
    input.trim(); // 改行コード除去
    if (input.length() > 0) {
      parseCommand(input);
    }
  }

  // 各機能の自動処理 (時間管理)
  updateSplash();
  updateMist();
  updateLED();
}

// ==========================================
// 5. ミストボタン制御 (Click Simulation)
// ==========================================
// タクトスイッチを一回「カチッ」と押す動作を再現
void clickMistButton() {
  digitalWrite(PIN_MIST, HIGH); // 押す
  delay(100);                   // 0.1秒キープ
  digitalWrite(PIN_MIST, LOW);  // 離す
  delay(100);                   // 連続入力防止
}

// ==========================================
// 6. コマンド解析 (Command Parser)
// ==========================================
void parseCommand(String input) {
  int firstComma = input.indexOf(',');
  String cmd = input.substring(0, firstComma);
  String params = input.substring(firstComma + 1);

  // --- FAN (風) ---
  // 例: FAN,1 (ON), FAN,0 (OFF)
  if (cmd.equalsIgnoreCase("FAN")) {
    int val = params.toInt();
    if (val == 1) {
      digitalWrite(PIN_FAN, HIGH);
      Serial.println("CMD: Fan ON");
    } else {
      digitalWrite(PIN_FAN, LOW);
      Serial.println("CMD: Fan OFF");
    }
  }
  
  // --- SPLASH (水) ---
  // 例: SPLASH
  else if (cmd.equalsIgnoreCase("SPLASH")) {
    splashActive = true;
    splashStartTime = millis();
    digitalWrite(PIN_SPLASH, HIGH);
    Serial.println("CMD: Splash Fired");
  }
  
  // --- MIST (ミスト) ---
  // 例: MIST,1 (一瞬), MIST,2 (継続), MIST,0 (OFF)
  else if (cmd.equalsIgnoreCase("MIST")) {
    int reqMode = params.toInt();
    
    if (reqMode == 1) { // 一瞬モード
      if (!isMistOn) {
        clickMistButton(); // OFFならONにする
        isMistOn = true;
      }
      mistAutoOffMode = 1; // タイマーセット
      mistStartTime = millis();
      Serial.println("CMD: Mist 1-Shot (Auto OFF set)");
    }
    else if (reqMode == 2) { // 継続モード
      mistAutoOffMode = 0; // タイマー解除
      if (!isMistOn) {
        clickMistButton(); // OFFならONにする
        isMistOn = true;
        Serial.println("CMD: Mist ON (Continuous)");
      } else {
        Serial.println("CMD: Mist already ON");
      }
    }
    else { // OFFモード
      mistAutoOffMode = 0;
      if (isMistOn) {
        clickMistButton(); // ONならOFFにする
        isMistOn = false;
        Serial.println("CMD: Mist OFF");
      } else {
        Serial.println("CMD: Mist already OFF");
      }
    }
  }
  
  // --- LED ---
  // 例: LED,色ID,強さ,光り方,切り替え
  else if (cmd.equalsIgnoreCase("LED")) {
    int p[4]; 
    int strIdx = 0;
    // パラメータを4つ読み取る
    for(int i=0; i<4; i++) {
      int nextComma = params.indexOf(',', strIdx);
      if(nextComma == -1) nextComma = params.length();
      p[i] = params.substring(strIdx, nextComma).toInt();
      strIdx = nextComma + 1;
    }
    
    ledTargetColorIdx = p[0];
    ledBrightnessMode = p[1];
    ledEffect         = p[2];
    ledTransition     = p[3];

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
    
    Serial.print("CMD: LED Set -> Color:"); Serial.println(ledTargetColorIdx);
  }
}

// ==========================================
// 7. 自動更新ロジック (Update Loop)
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
      // 時間が来たらOFFにする
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
  // 30msごとに更新 (滑らかなアニメーションのため)
  if (now - lastLedUpdate < 30) return;
  lastLedUpdate = now;

  // 目標色の取得
  uint32_t targetColorRaw = colors[ledTargetColorIdx];
  if (ledBrightnessMode == 0) targetColorRaw = colors[11]; // 強さ0なら消灯

  float tR = (uint8_t)(targetColorRaw >> 16);
  float tG = (uint8_t)(targetColorRaw >> 8);
  float tB = (uint8_t)(targetColorRaw);
  float tW = (uint8_t)(targetColorRaw >> 24);

  // フェード処理 (Transition)
  if (ledTransition == 1) {
    float step = 10.0; // 変化のスピード
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
    // 一瞬切り替えの場合はすでにParseCommandでセット済みだが念のため
    currentR = tR; currentG = tG; currentB = tB; currentW = tW;
  }

  // エフェクト計算 (点滅・呼吸)
  float brightnessFactor = 1.0;
  
  // 強さ係数
  if (ledBrightnessMode == 1) brightnessFactor = 0.2;       // 弱
  else if (ledBrightnessMode == 2) brightnessFactor = 1.0;  // 強
  else brightnessFactor = 0.0;                              // なし

  // エフェクト
  if (ledEffect == 1) { // 点滅 (Blink)
    if ((now / 250) % 2 == 0) brightnessFactor = 0; 
  } 
  else if (ledEffect == 2) { // 呼吸 (Breathe)
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

// ==========================================
// 8. 色定義 (Color Setup)
// ==========================================
void setupColors() {
  // フォーマット: strip.Color(R, G, B, W)
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