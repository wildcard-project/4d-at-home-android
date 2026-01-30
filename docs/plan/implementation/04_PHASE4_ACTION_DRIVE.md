# Phase 4: ActionDrive実装

**期間目安**: 3-4日  
**前提条件**: Phase 3 完了  
**優先度**: 高

---

## 📋 タスク一覧

| # | タスク | 優先度 | 完了条件 |
|:-:|:-------|:------:|:---------|
| 4.1 | ESP32 Motor1 BLEファームウェア | 必須 | BLE接続・振動制御動作 |
| 4.2 | ESP32 Motor2 BLEファームウェア | 必須 | BLE接続・振動制御動作 |
| 4.3 | ActionDriveコマンド定義 | 必須 | 全モード対応 |
| 4.4 | Android制御ロジック | 必須 | コマンド送信動作 |
| 4.5 | 制御画面UI拡張 | 必須 | 手動振動制御UI |
| 4.6 | 動作テスト | 必須 | 全振動モード確認 |

---

## 4.1 ESP32 Motor1 BLEファームウェア

### 4.1.1 ディレクトリ構造

```
esp32_firmware/
├── effect_station/
│   └── ...
├── action_drive_motor1/
│   ├── platformio.ini
│   └── src/
│       └── main.cpp
└── action_drive_motor2/
    ├── platformio.ini
    └── src/
        └── main.cpp
```

### 4.1.2 platformio.ini (Motor1/Motor2共通)

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino

; シリアルモニタ設定
monitor_speed = 115200

; アップロード設定
upload_speed = 921600

; ビルドフラグ (Motor1の場合)
build_flags = 
    -D LED_BUILTIN=2
    -D DEVICE_TYPE_ACTION_DRIVE_1
    ; Motor2の場合は -D DEVICE_TYPE_ACTION_DRIVE_2

lib_deps =
    ; 追加ライブラリなし
```

### 4.1.3 main.cpp (ActionDrive Motor1)

```cpp
/**
 * 4DX@HOME ActionDrive Motor1 - BLE Version
 * 
 * ESP32 BLE Server for Vibration Motor Control
 * 
 * Device Name: 4D_AD1_XXXX (XXXX = MAC下4桁)
 * 
 * 振動モード:
 * - OFF: 停止
 * - WEAK: 弱振動
 * - MEDIUM_WEAK: やや弱振動
 * - MEDIUM_STRONG: やや強振動
 * - STRONG: 強振動
 * - HEARTBEAT: 心臓の鼓動パターン
 * - RUMBLE_FAST: 高速ランブル
 * - RUMBLE_SLOW: 低速ランブル
 * 
 * ハードウェア:
 * - D5 (GPIO14): 強振動
 * - D6 (GPIO12): やや強振動
 * - D7 (GPIO13): やや弱振動
 * - D8 (GPIO15): 弱振動
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ==========================================
// 1. BLE定義
// ==========================================
#define SERVICE_UUID        "4D580001-0000-1000-8000-00805F9B34FB"
#define COMMAND_CHAR_UUID   "4D580002-0000-1000-8000-00805F9B34FB"
#define STATUS_CHAR_UUID    "4D580003-0000-1000-8000-00805F9B34FB"

// デバイスタイプ (Motor1 / Motor2)
#ifndef DEVICE_TYPE_ACTION_DRIVE_1
  #define DEVICE_TYPE_ACTION_DRIVE_2
#endif

#ifdef DEVICE_TYPE_ACTION_DRIVE_1
  #define DEVICE_PREFIX "4D_AD1_"
#else
  #define DEVICE_PREFIX "4D_AD2_"
#endif

// ==========================================
// 2. ピン定義 (モーター)
// ==========================================
const int PIN_STRONG      = 14;  // D5 - 強振動
const int PIN_MEDIUM_STRONG = 12; // D6 - やや強振動
const int PIN_MEDIUM_WEAK = 13;  // D7 - やや弱振動
const int PIN_WEAK        = 15;  // D8 - 弱振動

// ==========================================
// 3. 振動モード定義
// ==========================================
enum VibrationMode {
    MODE_OFF = 0,
    MODE_WEAK = 1,
    MODE_MEDIUM_WEAK = 2,
    MODE_MEDIUM_STRONG = 3,
    MODE_STRONG = 4,
    MODE_HEARTBEAT = 5,
    MODE_RUMBLE_FAST = 6,
    MODE_RUMBLE_SLOW = 7
};

const char* modeNames[] = {
    "OFF",
    "WEAK",
    "MEDIUM_WEAK",
    "MEDIUM_STRONG",
    "STRONG",
    "HEARTBEAT",
    "RUMBLE_FAST",
    "RUMBLE_SLOW"
};

// ==========================================
// 4. グローバル変数
// ==========================================

// BLE
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandCharacteristic = nullptr;
BLECharacteristic* pStatusCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// 振動
VibrationMode currentMode = MODE_OFF;
unsigned long lastPatternUpdate = 0;
int patternStep = 0;

// ==========================================
// 5. 関数プロトタイプ
// ==========================================
void parseCommand(String input);
void setMotorPins(bool strong, bool medStrong, bool medWeak, bool weak);
void stopAll();
void updatePattern();
void sendStatus(String status);
String getDeviceName();

// ==========================================
// 6. BLEコールバッククラス
// ==========================================

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        deviceConnected = true;
        Serial.println("[BLE] Client connected");
        sendStatus("CONNECTED");
    }

    void onDisconnect(BLEServer* pServer) override {
        deviceConnected = false;
        Serial.println("[BLE] Client disconnected");
        // 切断時に停止
        currentMode = MODE_OFF;
        stopAll();
    }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        String value = pCharacteristic->getValue().c_str();
        value.trim();
        
        if (value.length() > 0) {
            Serial.print("[BLE] Received command: ");
            Serial.println(value);
            parseCommand(value);
        }
    }
};

// ==========================================
// 7. セットアップ
// ==========================================
void setup() {
    Serial.begin(115200);
    Serial.println("\n===================================");
    #ifdef DEVICE_TYPE_ACTION_DRIVE_1
    Serial.println(" 4DX@HOME ActionDrive Motor1 (BLE)");
    #else
    Serial.println(" 4DX@HOME ActionDrive Motor2 (BLE)");
    #endif
    Serial.println("===================================");

    // ピン設定
    pinMode(PIN_STRONG, OUTPUT);
    pinMode(PIN_MEDIUM_STRONG, OUTPUT);
    pinMode(PIN_MEDIUM_WEAK, OUTPUT);
    pinMode(PIN_WEAK, OUTPUT);
    
    stopAll();

    // BLE初期化
    String deviceName = getDeviceName();
    Serial.print("Device name: ");
    Serial.println(deviceName);

    BLEDevice::init(deviceName.c_str());
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    // サービス作成
    BLEService* pService = pServer->createService(SERVICE_UUID);

    // Command Characteristic
    pCommandCharacteristic = pService->createCharacteristic(
        COMMAND_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_WRITE_NR
    );
    pCommandCharacteristic->setCallbacks(new CommandCallbacks());

    // Status Characteristic
    pStatusCharacteristic = pService->createCharacteristic(
        STATUS_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusCharacteristic->addDescriptor(new BLE2902());

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
    Serial.println("===================================\n");
}

// ==========================================
// 8. メインループ
// ==========================================
void loop() {
    // 再接続処理
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        BLEDevice::startAdvertising();
        Serial.println("[BLE] Restart advertising");
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    // パターンモードの更新
    updatePattern();
}

// ==========================================
// 9. ステータス送信
// ==========================================
void sendStatus(String status) {
    if (deviceConnected && pStatusCharacteristic != nullptr) {
        pStatusCharacteristic->setValue(status.c_str());
        pStatusCharacteristic->notify();
        Serial.print("[BLE] Status: ");
        Serial.println(status);
    }
}

// ==========================================
// 10. デバイス名生成
// ==========================================
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[20];
    sprintf(name, "%s%02X%02X", DEVICE_PREFIX, mac[4], mac[5]);
    return String(name);
}

// ==========================================
// 11. コマンド解析
// ==========================================
void parseCommand(String input) {
    input.toUpperCase();
    int commaPos = input.indexOf(',');
    String cmd = (commaPos > 0) ? input.substring(0, commaPos) : input;
    String param = (commaPos > 0) ? input.substring(commaPos + 1) : "";
    
    // MOTORコマンド: MOTOR,<mode>
    if (cmd == "MOTOR") {
        int mode = param.toInt();
        
        if (mode >= MODE_OFF && mode <= MODE_RUMBLE_SLOW) {
            currentMode = (VibrationMode)mode;
            patternStep = 0;
            lastPatternUpdate = millis();
            
            // 固定モードは即座に反映
            switch (currentMode) {
                case MODE_OFF:
                    stopAll();
                    break;
                case MODE_WEAK:
                    setMotorPins(false, false, false, true);
                    break;
                case MODE_MEDIUM_WEAK:
                    setMotorPins(false, false, true, false);
                    break;
                case MODE_MEDIUM_STRONG:
                    setMotorPins(false, true, false, false);
                    break;
                case MODE_STRONG:
                    setMotorPins(true, false, false, false);
                    break;
                case MODE_HEARTBEAT:
                case MODE_RUMBLE_FAST:
                case MODE_RUMBLE_SLOW:
                    // パターンモードはupdatePattern()で処理
                    break;
            }
            
            Serial.printf("CMD: Motor mode=%d (%s)\n", mode, modeNames[mode]);
            char statusMsg[32];
            sprintf(statusMsg, "MOTOR:%d", mode);
            sendStatus(statusMsg);
        } else {
            Serial.println("CMD: Invalid mode");
            sendStatus("ERR:INVALID_MODE");
        }
    }
    // 停止コマンド
    else if (cmd == "STOP") {
        currentMode = MODE_OFF;
        stopAll();
        Serial.println("CMD: Stop");
        sendStatus("MOTOR:0");
    }
    // 不明なコマンド
    else {
        Serial.print("Unknown command: ");
        Serial.println(input);
        sendStatus("ERR:UNKNOWN");
    }
}

// ==========================================
// 12. モーター制御
// ==========================================
void setMotorPins(bool strong, bool medStrong, bool medWeak, bool weak) {
    digitalWrite(PIN_STRONG, strong ? HIGH : LOW);
    digitalWrite(PIN_MEDIUM_STRONG, medStrong ? HIGH : LOW);
    digitalWrite(PIN_MEDIUM_WEAK, medWeak ? HIGH : LOW);
    digitalWrite(PIN_WEAK, weak ? HIGH : LOW);
}

void stopAll() {
    setMotorPins(false, false, false, false);
}

// ==========================================
// 13. パターンモード更新
// ==========================================
void updatePattern() {
    unsigned long now = millis();
    
    switch (currentMode) {
        case MODE_HEARTBEAT: {
            // 心臓の鼓動パターン: ドクン...ドクン...
            // 0: 強 100ms → 1: OFF 100ms → 2: やや強 80ms → 3: OFF 700ms
            unsigned long intervals[] = {100, 100, 80, 700};
            
            if (now - lastPatternUpdate >= intervals[patternStep]) {
                lastPatternUpdate = now;
                patternStep = (patternStep + 1) % 4;
                
                switch (patternStep) {
                    case 0: setMotorPins(true, false, false, false); break;
                    case 1: stopAll(); break;
                    case 2: setMotorPins(false, true, false, false); break;
                    case 3: stopAll(); break;
                }
            }
            break;
        }
        
        case MODE_RUMBLE_FAST: {
            // 高速ランブル: 40ms ON / 40ms OFF
            if (now - lastPatternUpdate >= 40) {
                lastPatternUpdate = now;
                patternStep = !patternStep;
                
                if (patternStep) {
                    setMotorPins(true, false, false, false);
                } else {
                    stopAll();
                }
            }
            break;
        }
        
        case MODE_RUMBLE_SLOW: {
            // 低速ランブル: 200ms ON / 200ms OFF
            if (now - lastPatternUpdate >= 200) {
                lastPatternUpdate = now;
                patternStep = !patternStep;
                
                if (patternStep) {
                    setMotorPins(false, true, false, false);
                } else {
                    stopAll();
                }
            }
            break;
        }
        
        default:
            // 固定モードは何もしない
            break;
    }
}
```

---

## 4.2 ESP32 Motor2 BLEファームウェア

Motor2は上記のMotor1と同じコードで、`platformio.ini`のビルドフラグのみ変更:

```ini
build_flags = 
    -D LED_BUILTIN=2
    -D DEVICE_TYPE_ACTION_DRIVE_2
```

これにより、デバイス名が `4D_AD2_XXXX` になります。

---

## 4.3 ActionDriveコマンド定義

### 4.3.1 domain/model/ActionDriveCommand.kt

```kotlin
package com.wildcard.fourd_at_home.domain.model

/**
 * ActionDrive 振動モード定義
 */
enum class VibrationMode(val value: Int, val displayName: String, val emoji: String) {
    OFF(0, "停止", "⏹️"),
    WEAK(1, "弱", "〰️"),
    MEDIUM_WEAK(2, "やや弱", "🔉"),
    MEDIUM_STRONG(3, "やや強", "🔊"),
    STRONG(4, "強", "💪"),
    HEARTBEAT(5, "心臓の鼓動", "💓"),
    RUMBLE_FAST(6, "高速ランブル", "⚡"),
    RUMBLE_SLOW(7, "低速ランブル", "🌊");

    companion object {
        fun fromValue(value: Int): VibrationMode = 
            entries.find { it.value == value } ?: OFF
    }
}

/**
 * ActionDriveコマンドビルダー
 */
object ActionDriveCommands {
    
    /**
     * 振動モード設定
     */
    fun motor(mode: VibrationMode): String = "MOTOR,${mode.value}"
    
    /**
     * 停止
     */
    fun stop(): String = "STOP"
    
    // === ショートカット ===
    fun off(): String = motor(VibrationMode.OFF)
    fun weak(): String = motor(VibrationMode.WEAK)
    fun mediumWeak(): String = motor(VibrationMode.MEDIUM_WEAK)
    fun mediumStrong(): String = motor(VibrationMode.MEDIUM_STRONG)
    fun strong(): String = motor(VibrationMode.STRONG)
    fun heartbeat(): String = motor(VibrationMode.HEARTBEAT)
    fun rumbleFast(): String = motor(VibrationMode.RUMBLE_FAST)
    fun rumbleSlow(): String = motor(VibrationMode.RUMBLE_SLOW)
}
```

---

## 4.4 Android制御ロジック

### 4.4.1 domain/effect/ActionDriveController.kt

```kotlin
package com.wildcard.fourd_at_home.domain.effect

import com.wildcard.fourd_at_home.ble.CommandSender
import com.wildcard.fourd_at_home.domain.model.ActionDriveCommands
import com.wildcard.fourd_at_home.domain.model.VibrationMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 振動対象モーター
 */
enum class MotorTarget {
    MOTOR_1,    // 左モーター
    MOTOR_2,    // 右モーター
    BOTH        // 両方
}

@Singleton
class ActionDriveController @Inject constructor(
    private val commandSender: CommandSender
) {
    
    /**
     * 指定モーターに振動モードを設定
     */
    suspend fun setVibration(
        target: MotorTarget,
        mode: VibrationMode
    ): List<Result<Unit>> {
        val command = ActionDriveCommands.motor(mode)
        return when (target) {
            MotorTarget.MOTOR_1 -> listOf(commandSender.sendToActionDrive1(command))
            MotorTarget.MOTOR_2 -> listOf(commandSender.sendToActionDrive2(command))
            MotorTarget.BOTH -> listOf(
                commandSender.sendToActionDrive1(command),
                commandSender.sendToActionDrive2(command)
            )
        }
    }
    
    /**
     * 指定モーターを停止
     */
    suspend fun stop(target: MotorTarget): List<Result<Unit>> {
        return setVibration(target, VibrationMode.OFF)
    }
    
    /**
     * 全モーター停止
     */
    suspend fun stopAll(): List<Result<Unit>> {
        return stop(MotorTarget.BOTH)
    }
    
    // === ショートカットメソッド (両モーター対象) ===
    
    suspend fun weak(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.WEAK)
    
    suspend fun mediumWeak(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.MEDIUM_WEAK)
    
    suspend fun mediumStrong(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.MEDIUM_STRONG)
    
    suspend fun strong(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.STRONG)
    
    suspend fun heartbeat(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.HEARTBEAT)
    
    suspend fun rumbleFast(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.RUMBLE_FAST)
    
    suspend fun rumbleSlow(): List<Result<Unit>> = 
        setVibration(MotorTarget.BOTH, VibrationMode.RUMBLE_SLOW)
    
    // === 個別モーター制御 ===
    
    suspend fun motor1(mode: VibrationMode): Result<Unit> {
        return commandSender.sendToActionDrive1(ActionDriveCommands.motor(mode))
    }
    
    suspend fun motor2(mode: VibrationMode): Result<Unit> {
        return commandSender.sendToActionDrive2(ActionDriveCommands.motor(mode))
    }
}
```

---

## 4.5 制御画面UI拡張

### 4.5.1 ui/control/ControlViewModel.kt (ActionDrive追加)

```kotlin
// 既存のControlViewModelに追加

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val bleDeviceManager: BleDeviceManager,
    private val effectStationController: EffectStationController,
    private val actionDriveController: ActionDriveController  // 追加
) : ViewModel() {
    
    // === ActionDrive ===
    
    fun setVibration(target: MotorTarget, mode: VibrationMode) {
        viewModelScope.launch {
            actionDriveController.setVibration(target, mode)
        }
    }
    
    fun stopMotors() {
        viewModelScope.launch {
            actionDriveController.stopAll()
        }
    }
    
    fun weak() {
        viewModelScope.launch {
            actionDriveController.weak()
        }
    }
    
    fun mediumWeak() {
        viewModelScope.launch {
            actionDriveController.mediumWeak()
        }
    }
    
    fun mediumStrong() {
        viewModelScope.launch {
            actionDriveController.mediumStrong()
        }
    }
    
    fun strong() {
        viewModelScope.launch {
            actionDriveController.strong()
        }
    }
    
    fun heartbeat() {
        viewModelScope.launch {
            actionDriveController.heartbeat()
        }
    }
    
    fun rumbleFast() {
        viewModelScope.launch {
            actionDriveController.rumbleFast()
        }
    }
    
    fun rumbleSlow() {
        viewModelScope.launch {
            actionDriveController.rumbleSlow()
        }
    }
    
    // 全停止(EffectStation + ActionDrive)
    fun stopAll() {
        viewModelScope.launch {
            effectStationController.stopAll()
            actionDriveController.stopAll()
            _fanOn.value = false
        }
    }
}
```

### 4.5.2 ui/control/ActionDriveControlPanel.kt

```kotlin
package com.wildcard.fourd_at_home.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wildcard.fourd_at_home.domain.effect.MotorTarget
import com.wildcard.fourd_at_home.domain.model.VibrationMode
import com.wildcard.fourd_at_home.ui.theme.EffectOrange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionDriveControlPanel(
    motor1Connected: Boolean,
    motor2Connected: Boolean,
    onSetVibration: (MotorTarget, VibrationMode) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anyConnected = motor1Connected || motor2Connected
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = EffectOrange.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎮 ActionDrive (振動)",
                style = MaterialTheme.typography.titleMedium,
                color = EffectOrange
            )
            
            if (!anyConnected) {
                Text(
                    text = "モーターが接続されていません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 接続状態表示
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotorStatusChip("Motor1", motor1Connected)
                    MotorStatusChip("Motor2", motor2Connected)
                }
                
                // 振動モード選択
                Text(
                    text = "振動モード（両モーター）",
                    style = MaterialTheme.typography.labelMedium
                )
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VibrationMode.entries.filter { it != VibrationMode.OFF }.forEach { mode ->
                        VibrationButton(
                            mode = mode,
                            onClick = { onSetVibration(MotorTarget.BOTH, mode) }
                        )
                    }
                }
                
                // 停止ボタン
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⏹️ 停止")
                }
            }
        }
    }
}

@Composable
private fun MotorStatusChip(name: String, connected: Boolean) {
    val color = if (connected) Color(0xFF4CAF50) else Color.Gray
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        )
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = if (connected) "✓" else "✗",
                color = color
            )
            Text(
                text = " $name",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun VibrationButton(
    mode: VibrationMode,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (mode) {
                VibrationMode.WEAK -> Color(0xFF8BC34A)
                VibrationMode.MEDIUM_WEAK -> Color(0xFFCDDC39)
                VibrationMode.MEDIUM_STRONG -> Color(0xFFFF9800)
                VibrationMode.STRONG -> Color(0xFFF44336)
                VibrationMode.HEARTBEAT -> Color(0xFFE91E63)
                VibrationMode.RUMBLE_FAST -> Color(0xFF9C27B0)
                VibrationMode.RUMBLE_SLOW -> Color(0xFF3F51B5)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Text("${mode.emoji} ${mode.displayName}")
    }
}
```

### 4.5.3 ControlScreenへの統合

```kotlin
// ControlScreen.kt 内のEffectStationControlPanelの後に追加

// ActionDrive制御
if (uiState.actionDrive1Connected || uiState.actionDrive2Connected) {
    ActionDriveControlPanel(
        motor1Connected = uiState.actionDrive1Connected,
        motor2Connected = uiState.actionDrive2Connected,
        onSetVibration = viewModel::setVibration,
        onStop = viewModel::stopMotors
    )
}
```

---

## 4.6 動作テスト

### テスト項目チェックリスト

| # | テスト項目 | 手順 | 期待結果 |
|:-:|:-----------|:-----|:---------|
| 1 | Motor1 起動確認 | ESP32の電源投入、シリアルモニタ確認 | 4D_AD1_XXXX表示、アドバタイズ開始 |
| 2 | Motor2 起動確認 | ESP32の電源投入、シリアルモニタ確認 | 4D_AD2_XXXX表示、アドバタイズ開始 |
| 3 | BLEスキャン | Android設定画面でスキャン | 4D_AD1/AD2が検出される |
| 4 | 両デバイス接続 | 2台とも接続ボタンタップ | 両方READY状態になる |
| 5 | 弱振動 | 制御画面で「弱」ボタン | 両モーターが弱く振動 |
| 6 | やや弱振動 | 制御画面で「やや弱」ボタン | 両モーターがやや弱く振動 |
| 7 | やや強振動 | 制御画面で「やや強」ボタン | 両モーターがやや強く振動 |
| 8 | 強振動 | 制御画面で「強」ボタン | 両モーターが強く振動 |
| 9 | 心臓の鼓動 | 制御画面で「心臓の鼓動」ボタン | ドクン...ドクン...パターン |
| 10 | 高速ランブル | 制御画面で「高速ランブル」ボタン | 高速でブルブル振動 |
| 11 | 低速ランブル | 制御画面で「低速ランブル」ボタン | ゆっくりブルブル振動 |
| 12 | 停止 | 制御画面で「停止」ボタン | 振動が止まる |
| 13 | 全停止 | 制御画面で全停止ボタン | ES + 両モーター停止 |
| 14 | 切断時停止 | BLE接続を切断 | 自動的に振動停止 |

---

## ✅ Phase 4 完了チェックリスト

- [ ] ESP32 Motor1 BLEファームウェアが動作する
- [ ] ESP32 Motor2 BLEファームウェアが動作する
- [ ] AndroidからMotor1に接続できる
- [ ] AndroidからMotor2に接続できる
- [ ] 弱振動が動作する
- [ ] やや弱振動が動作する
- [ ] やや強振動が動作する
- [ ] 強振動が動作する
- [ ] 心臓の鼓動パターンが動作する
- [ ] 高速ランブルが動作する
- [ ] 低速ランブルが動作する
- [ ] 停止が動作する
- [ ] BLE切断時に自動停止する

---

## 📝 次のPhase

[Phase 5: 再生同期エンジン](./05_PHASE5_PLAYBACK_SYNC.md) へ進む
