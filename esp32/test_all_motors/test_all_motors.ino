// 動作確認用：8個のモーターを1個ずつ順番に 1 秒ずつ回す
// 本番のファームウェアは esp32/action_drive。確認が終わったらそちらを書き込み直す。

#include <Arduino.h>

struct Motor { uint8_t pin; const char *name; };

static const Motor MOTORS[] = {
  {23, "お尻 強"}, {22, "お尻 中強"}, {21, "お尻 中弱"}, {19, "お尻 弱"},
  {25, "背中 強"}, {26, "背中 中強"}, {27, "背中 中弱"}, {32, "背中 弱"},
};
static const size_t N = sizeof(MOTORS) / sizeof(MOTORS[0]);
static const uint8_t PIN_LED = 2;
static const uint32_t STEP_MS = 1000;

void setup() {
  Serial.begin(115200);
  for (size_t i = 0; i < N; i++) {
    pinMode(MOTORS[i].pin, OUTPUT);
    digitalWrite(MOTORS[i].pin, LOW);
  }
  pinMode(PIN_LED, OUTPUT);
  Serial.println("8個のモーターを1個ずつ 1 秒ずつ回します");
}

void loop() {
  static size_t cur = 0;
  // 同時に2個 ON にならないよう、前のモーターを切ってから次を入れる
  for (size_t i = 0; i < N; i++) digitalWrite(MOTORS[i].pin, LOW);
  digitalWrite(MOTORS[cur].pin, HIGH);
  digitalWrite(PIN_LED, cur % 2 ? LOW : HIGH);
  Serial.printf("%u: %s (GPIO%u)\n", (unsigned)cur + 1, MOTORS[cur].name, MOTORS[cur].pin);
  cur = (cur + 1) % N;
  delay(STEP_MS);
}
