// 動作確認用：お尻「弱」（GPIO19）を 5 秒おきに ON / OFF する
// 本番のファームウェアは esp32/action_drive。確認が終わったらそちらを書き込み直す。

#include <Arduino.h>

static const uint8_t PIN_TEST = 19;    // お尻 弱
static const uint8_t PIN_LED = 2;      // 基板の青い LED（ON の間だけ点く）
static const uint32_t INTERVAL_MS = 5000;

// ほかのモーターのピン。勝手に回らないように LOW に固定する
static const uint8_t OTHER_PINS[] = {23, 22, 21, 25, 26, 27, 32};

void setup() {
  Serial.begin(115200);
  for (uint8_t p : OTHER_PINS) {
    pinMode(p, OUTPUT);
    digitalWrite(p, LOW);
  }
  pinMode(PIN_TEST, OUTPUT);
  digitalWrite(PIN_TEST, LOW);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);
  Serial.println("お尻 弱（GPIO19）を 5 秒おきに ON/OFF します");
}

void loop() {
  static bool on = false;
  on = !on;
  digitalWrite(PIN_TEST, on ? HIGH : LOW);
  digitalWrite(PIN_LED, on ? HIGH : LOW);
  Serial.println(on ? "ON" : "OFF");
  delay(INTERVAL_MS);
}
