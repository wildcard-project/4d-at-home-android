# BLE通信プロトコル詳細仕様書

**バージョン**: 1.0.0  
**作成日**: 2026年1月30日  
**対象プロジェクト**: bluetooth_test  

---

## 目次

1. [概要](#1-概要)
2. [通信アーキテクチャ](#2-通信アーキテクチャ)
3. [GATT仕様](#3-gatt仕様)
4. [コマンド体系](#4-コマンド体系)
5. [接続シーケンス](#5-接続シーケンス)
6. [データ送受信](#6-データ送受信)
7. [エラーハンドリング](#7-エラーハンドリング)
8. [タイミング仕様](#8-タイミング仕様)
9. [複数デバイス管理](#9-複数デバイス管理)
10. [セキュリティ](#10-セキュリティ)

---

## 1. 概要

### 1.1 プロトコル概要

本プロトコルは、AndroidアプリケーションとESP32デバイス間のBluetooth Low Energy (BLE) 通信を定義する。GATTプロファイルを使用し、Characteristicへの書き込みによるコマンド送信と、Notifyによるステータス通知を実現する。

### 1.2 通信方式

| 項目 | 仕様 |
|:-----|:-----|
| **プロトコル** | Bluetooth Low Energy (BLE 4.2) |
| **プロファイル** | GATT (Generic Attribute Profile) |
| **ロール** | Android: Central (Client) / ESP32: Peripheral (Server) |
| **接続形態** | 1対多 (Android 1台 → ESP32 最大7台) |
| **通信方向** | 双方向（コマンド: Central→Peripheral / ステータス: Peripheral→Central） |

### 1.3 プロトコルスタック

```
┌─────────────────────────────────────────────────────┐
│              Application Layer                       │
│  (コマンド送信、ステータス受信、エフェクト制御)        │
├─────────────────────────────────────────────────────┤
│                  GATT Layer                          │
│  (Service, Characteristic, Descriptor)              │
├─────────────────────────────────────────────────────┤
│                  ATT Layer                           │
│  (Attribute Protocol)                               │
├─────────────────────────────────────────────────────┤
│                  L2CAP Layer                         │
│  (Logical Link Control and Adaptation Protocol)     │
├─────────────────────────────────────────────────────┤
│               Link Layer (LL)                        │
│  (Connection, Advertising, Scanning)                │
├─────────────────────────────────────────────────────┤
│              Physical Layer (PHY)                    │
│  (2.4 GHz ISM Band, GFSK modulation)               │
└─────────────────────────────────────────────────────┘
```

---

## 2. 通信アーキテクチャ

### 2.1 システム構成図

```
┌──────────────────────────────────────────────────────────────┐
│                      Android Device                          │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                   Application                           │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │  │
│  │  │EffectRouter │  │PlaybackVM    │  │SettingsVM   │  │  │
│  │  └──────┬───────┘  └──────────────┘  └──────────────┘  │  │
│  └─────────┼──────────────────────────────────────────────┘  │
│            ▼                                                  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │               BleDeviceManager                          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐             │  │
│  │  │Connection│  │Connection│  │Connection│             │  │
│  │  │  ESP #1  │  │  ESP #2  │  │  ESP #3  │             │  │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘             │  │
│  └───────┼─────────────┼─────────────┼───────────────────┘  │
└──────────┼─────────────┼─────────────┼───────────────────────┘
           │ BLE         │ BLE         │ BLE
           ▼             ▼             ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │ESP32 #1  │  │ESP32 #2  │  │ESP32 #3  │
    │4D_LED1_  │  │4D_LED2_  │  │4D_Device_│
    │  A1B2    │  │  C3D4    │  │  E5F6    │
    └──────────┘  └──────────┘  └──────────┘
```

### 2.2 Central/Peripheral ロール

#### Central (Android)

- デバイスのスキャン実行
- 接続の開始
- サービス/Characteristicの探索
- コマンドの書き込み
- Notifyの購読

#### Peripheral (ESP32)

- アドバタイジング
- 接続の受け入れ
- GATTサービスの提供
- コマンドの受信・処理
- ステータスの通知

---

## 3. GATT仕様

### 3.1 サービス定義

#### 4DX Effect Service

| 属性 | 値 |
|:-----|:---|
| **Service UUID** | `4D580001-0000-1000-8000-00805F9B34FB` |
| **Service Type** | Primary Service |
| **説明** | 4DXエフェクト制御用カスタムサービス |

### 3.2 Characteristic定義

#### Command Characteristic

| 属性 | 値 |
|:-----|:---|
| **UUID** | `4D580002-0000-1000-8000-00805F9B34FB` |
| **Properties** | Write, Write Without Response |
| **Permission** | WRITABLE |
| **Value** | UTF-8エンコードのコマンド文字列 |
| **Max Length** | 20 bytes (BLE ATT MTU制限) |

#### Status Characteristic

| 属性 | 値 |
|:-----|:---|
| **UUID** | `4D580003-0000-1000-8000-00805F9B34FB` |
| **Properties** | Read, Notify |
| **Permission** | READABLE |
| **Value** | UTF-8エンコードのステータス文字列 |
| **Descriptor** | CCCD (Client Characteristic Configuration Descriptor) |

### 3.3 Descriptor定義

#### Client Characteristic Configuration Descriptor (CCCD)

| 属性 | 値 |
|:-----|:---|
| **UUID** | `00002902-0000-1000-8000-00805f9b34fb` |
| **Value** | 0x0000 (Disabled) / 0x0001 (Notify Enabled) |

### 3.4 GATT構成図

```mermaid
graph TB
    GS[GATT Server ESP32]
    
    subgraph Service["Service: 4D580001-...-00805F9B34FB<br/>4DX Effect Service<br/>Type: Primary"]
        CC["Characteristic: 4D580002-..." <br/> Command <br/> ■ Properties: Write, WriteNR <br/> ■ Value: コマンド文字列"]
        
        SC["Characteristic: 4D580003-..." <br/> Status <br/> ■ Properties: Read, Notify <br/> ■ Value: ステータス文字列"]
        
        CCCD["Descriptor: 00002902-..." <br/> CCCD <br/> ■ Value: 0x0000 (Disabled) <br/> ■ Value: 0x0001 (Notify Enabled)"]
        
        SC --> CCCD
    end
    
    GS --> Service
    
    style GS fill:#2196f3,color:#fff
    style Service fill:#e3f2fd
    style CC fill:#fff3e0
    style SC fill:#e8f5e9
    style CCCD fill:#ffebee
```

```
GATT Server (ESP32)
│
└── Service: 4D580001-0000-1000-8000-00805F9B34FB
    │   名称: 4DX Effect Service
    │   タイプ: Primary
    │
    ├── Characteristic: 4D580002-... (Command)
    │   │   プロパティ: Write, Write No Response
    │   │   値: コマンド文字列 (例: "L1_ON")
    │   │
    │   └── (Descriptorなし)
    │
    └── Characteristic: 4D580003-... (Status)
        │   プロパティ: Read, Notify
        │   値: ステータス文字列 (例: "LED1:ON")
        │
        └── Descriptor: 00002902-... (CCCD)
                値: 0x0001 (Notify有効時)
```

---

## 4. コマンド体系

### 4.1 コマンドフォーマット

```
[デバイス/エフェクト識別子]_[操作/モード]
```

### 4.2 LEDコマンド（現在実装済み）

#### LED1コマンド

| コマンド | 説明 | 応答ステータス |
|:---------|:-----|:---------------|
| `L1_ON` | LED1を点灯 | `LED1:ON` |
| `L1_OFF` | LED1を消灯 | `LED1:OFF` |
| `L1_BL` | LED1を点滅 | `LED1:BLINK` |

#### LED2コマンド

| コマンド | 説明 | 応答ステータス |
|:---------|:-----|:---------------|
| `L2_ON` | LED2を点灯 | `LED2:ON` |
| `L2_OFF` | LED2を消灯 | `LED2:OFF` |
| `L2_BL` | LED2を点滅 | `LED2:BLINK` |

### 4.3 拡張エフェクトコマンド（将来対応）

#### 振動（Vibration）

| コマンド | 説明 | モード |
|:---------|:-----|:-------|
| `V_UW` | 背中・弱 | up_weak |
| `V_UM1` | 背中・中弱 | up_mid_weak |
| `V_UM2` | 背中・中強 | up_mid_strong |
| `V_US` | 背中・強 | up_strong |
| `V_DW` | おしり・弱 | down_weak |
| `V_DM1` | おしり・中弱 | down_mid_weak |
| `V_DM2` | おしり・中強 | down_mid_strong |
| `V_DS` | おしり・強 | down_strong |
| `V_BW` | 両方・弱 | up_down_weak |
| `V_BM1` | 両方・中弱 | up_down_mid_weak |
| `V_BM2` | 両方・中強 | up_down_mid_strong |
| `V_BS` | 両方・強 | up_down_strong |
| `V_HB` | ドキドキ | heartbeat |
| `V_OFF` | 振動停止 | - |

#### 光（Flash）

| コマンド | 説明 | モード |
|:---------|:-----|:-------|
| `F_ON` | 点灯 | steady |
| `F_SB` | 遅い点滅 | slow_blink |
| `F_FB` | 早い点滅 | fast_blink |
| `F_OFF` | 消灯 | - |

#### 風（Wind）

| コマンド | 説明 | モード |
|:---------|:-----|:-------|
| `W_ON` | 風ON | on |
| `W_OFF` | 風OFF | - |

#### 水（Water）

| コマンド | 説明 | モード |
|:---------|:-----|:-------|
| `A_BT` | 水噴射 | burst |

#### 色（Color）

| コマンド | 説明 | モード |
|:---------|:-----|:-------|
| `C_RD` | 赤 | red |
| `C_GR` | 緑 | green |
| `C_BL` | 青 | blue |
| `C_YL` | 黄色 | yellow |
| `C_CY` | シアン | cyan |
| `C_PP` | 紫 | purple |
| `C_OFF` | 消灯 | - |

### 4.4 コマンドマッピング表（Android側）

```kotlin
private val startCommandMap = mapOf(
    // LED
    "led1:on" to "L1_ON",
    "led1:blink" to "L1_BL",
    "led2:on" to "L2_ON",
    "led2:blink" to "L2_BL",
    
    // Vibration
    "vibration:up_weak" to "V_UW",
    "vibration:heartbeat" to "V_HB",
    // ...
    
    // Flash
    "flash:steady" to "F_ON",
    "flash:fast_blink" to "F_FB",
    // ...
)

private val stopCommandMap = mapOf(
    EffectType.LED1 to "L1_OFF",
    EffectType.LED2 to "L2_OFF",
    EffectType.VIBRATION to "V_OFF",
    EffectType.FLASH to "F_OFF",
    EffectType.WIND to "W_OFF",
    EffectType.COLOR to "C_OFF"
)
```

---

## 5. 接続シーケンス

### 5.1 スキャン～接続シーケンス

```
┌─────────┐                           ┌─────────┐
│ Android │                           │  ESP32  │
│ (Central)│                          │(Periph) │
└────┬────┘                           └────┬────┘
     │                                     │
     │         Advertising                 │
     │ ◀─────────────────────────────────  │
     │  Name: 4D_LED1_A1B2                 │
     │  Service UUID: 4D580001...         │
     │                                     │
     │         Scan Request               │
     │ ─────────────────────────────────▶  │
     │                                     │
     │         Scan Response              │
     │ ◀─────────────────────────────────  │
     │                                     │
     │         Connect Request            │
     │ ─────────────────────────────────▶  │
     │                                     │
     │         Connect Response           │
     │ ◀─────────────────────────────────  │
     │                                     │
     │  onConnectionStateChange           │
     │  (STATE_CONNECTED)                 │
     │                                     │
     │         Discover Services          │
     │ ─────────────────────────────────▶  │
     │                                     │
     │         Services Response          │
     │ ◀─────────────────────────────────  │
     │  Service: 4D580001...              │
     │  Char: 4D580002... (Command)       │
     │  Char: 4D580003... (Status)        │
     │                                     │
     │  onServicesDiscovered              │
     │  (GATT_SUCCESS)                    │
     │                                     │
     │    ★ 接続完了 (READY状態)           │
     │                                     │
```

### 5.2 Notify購読シーケンス

```
┌─────────┐                           ┌─────────┐
│ Android │                           │  ESP32  │
└────┬────┘                           └────┬────┘
     │                                     │
     │   Write CCCD (0x0001)              │
     │ ─────────────────────────────────▶  │
     │   Enable Notification              │
     │                                     │
     │   Write Response                   │
     │ ◀─────────────────────────────────  │
     │                                     │
     │   ★ Notify購読完了                  │
     │                                     │
     │        (状態変化時)                  │
     │                                     │
     │   Notification                     │
     │ ◀─────────────────────────────────  │
     │   Value: "LED1:ON"                 │
     │                                     │
```

### 5.3 切断～再接続シーケンス

```
┌─────────┐                           ┌─────────┐
│ Android │                           │  ESP32  │
└────┬────┘                           └────┬────┘
     │                                     │
     │   Disconnect                       │
     │ ─────────────────────────────────▶  │
     │                                     │
     │   onConnectionStateChange          │
     │   (STATE_DISCONNECTED)             │
     │                                     │
     │                                     │
     │                     delay(500ms)    │
     │                                     │
     │         Advertising (再開)          │
     │ ◀─────────────────────────────────  │
     │                                     │
     │    （再接続可能状態）                 │
     │                                     │
```

---

## 6. データ送受信

### 6.1 コマンド送信（Android → ESP32）

#### 送信処理フロー

```kotlin
suspend fun sendCommand(address: String, command: String): Result<Unit> {
    // 1. GATT/Characteristic取得
    val gatt = gattMap[address]
    val characteristic = commandCharacteristics[address]
    
    // 2. コマンドをバイト列に変換
    val data = command.toByteArray(Charsets.UTF_8)
    
    // 3. Characteristicに書き込み
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(
            characteristic,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
    } else {
        characteristic.value = data
        characteristic.writeType = WRITE_TYPE_NO_RESPONSE
        gatt.writeCharacteristic(characteristic)
    }
    
    // 4. 操作間隔を確保
    delay(GATT_OPERATION_DELAY_MS)  // 100ms
    
    return Result.success(Unit)
}
```

#### 書き込みタイプ

| タイプ | 説明 | 用途 |
|:-------|:-----|:-----|
| WRITE_TYPE_DEFAULT | 書き込み応答あり | 信頼性重視 |
| WRITE_TYPE_NO_RESPONSE | 書き込み応答なし | 速度重視（本システム採用） |

### 6.2 ステータス受信（ESP32 → Android）

#### ESP32側（送信）

```cpp
void sendStatus(const String& status) {
    if (deviceConnected && pStatusCharacteristic != nullptr) {
        pStatusCharacteristic->setValue(status.c_str());
        pStatusCharacteristic->notify();
    }
}
```

#### Android側（受信）

```kotlin
class StatusCallbacks : BLECharacteristicCallbacks {
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val status = characteristic.getStringValue(0)
        Log.d(TAG, "Status received: $status")
        // ステータス処理
    }
}
```

### 6.3 データフォーマット

#### コマンドデータ

```mermaid
packet-beta
0-159: "Command Packet (Max 20 bytes)"
0-39: "UTF-8 String"
40-159: "Unused"
```

```
┌─────────────────────────────────────────┐
│            Command Packet               │
├─────────────────────────────────────────┤
│  UTF-8 String (Max 20 bytes)           │
│  例: "L1_ON" (5 bytes)                  │
│  例: "V_HB" (4 bytes)                   │
└─────────────────────────────────────────┘
```

#### ステータスデータ

```mermaid
packet-beta
0-159: "Status Packet (Max 20 bytes)"
0-71: "UTF-8 String"
72-159: "Unused"
```

```
┌─────────────────────────────────────────┐
│            Status Packet                │
├─────────────────────────────────────────┤
│  UTF-8 String (Max 20 bytes)           │
│  例: "CONNECTED" (9 bytes)              │
│  例: "LED1:ON" (7 bytes)                │
│  例: "ERR:UNKNOWN" (11 bytes)           │
└─────────────────────────────────────────┘
```

---

## 7. エラーハンドリング

### 7.1 エラー種別

| エラー種別 | 発生箇所 | 対処 |
|:-----------|:---------|:-----|
| **Bluetooth無効** | Android | ユーザーにBT有効化を促す |
| **権限不足** | Android | ランタイム権限リクエスト |
| **スキャン失敗** | Android | BLEスキャナー再初期化 |
| **接続タイムアウト** | Android | 再接続試行（最大3回） |
| **サービス未発見** | Android | 接続をクローズし再接続 |
| **書き込み失敗** | Android/ESP32 | ログ記録、UI通知 |
| **切断** | 両側 | 自動再アドバタイズ（ESP32）、状態更新（Android） |
| **不明コマンド** | ESP32 | エラーステータス通知 |

### 7.2 Android側エラー処理

```kotlin
sealed class BleError(open val message: String) {
    data class BluetoothDisabled(
        override val message: String = "Bluetoothが無効です"
    ) : BleError(message)
    
    data class PermissionDenied(
        override val message: String = "Bluetooth権限がありません"
    ) : BleError(message)
    
    data class ConnectionFailed(
        override val message: String,
        val statusCode: Int? = null
    ) : BleError(message)
    
    data class ServiceDiscoveryFailed(
        override val message: String = "サービスが見つかりません"
    ) : BleError(message)
    
    data class WriteFailed(
        override val message: String,
        val statusCode: Int? = null
    ) : BleError(message)
    
    data class Timeout(
        override val message: String = "タイムアウトしました"
    ) : BleError(message)
    
    data class DeviceNotFound(
        override val message: String = "デバイスが見つかりません"
    ) : BleError(message)
}
```

### 7.3 ESP32側エラー処理

```cpp
void processCommand(const String& cmd) {
    if (cmd.startsWith("L1_")) {
        // 正常処理
    }
    else if (cmd.startsWith("L2_")) {
        // LED1専用機では無視
        Serial.println("Ignoring LED2 command");
    }
    else {
        // 不明コマンド
        Serial.print("Unknown command: ");
        Serial.println(cmd);
        sendStatus("ERR:UNKNOWN");
    }
}
```

### 7.4 再接続ロジック

```kotlin
// Android側
private suspend fun connectWithRetry(
    device: BluetoothDevice,
    maxAttempts: Int = MAX_RECONNECT_ATTEMPTS
): Result<BleConnection> {
    var lastError: Exception? = null
    
    repeat(maxAttempts) { attempt ->
        Log.d(TAG, "Connection attempt ${attempt + 1}/$maxAttempts")
        
        try {
            val result = connectInternal(device)
            if (result.isSuccess) {
                return result
            }
        } catch (e: Exception) {
            lastError = e
        }
        
        delay(RECONNECT_DELAY_MS)  // 2000ms
    }
    
    return Result.failure(
        lastError ?: Exception("Connection failed after $maxAttempts attempts")
    )
}
```

---

## 8. タイミング仕様

### 8.1 タイムアウト値

| パラメータ | 値 | 説明 |
|:-----------|:---|:-----|
| CONNECTION_TIMEOUT_MS | 10,000 ms | 接続タイムアウト |
| SCAN_TIMEOUT_MS | 15,000 ms | スキャンタイムアウト |
| WRITE_TIMEOUT_MS | 5,000 ms | 書き込みタイムアウト |
| GATT_OPERATION_DELAY_MS | 100 ms | GATT操作間隔 |
| RECONNECT_DELAY_MS | 2,000 ms | 再接続待機時間 |
| SCAN_REPORT_DELAY_MS | 500 ms | スキャン結果報告間隔 |

### 8.2 BLE接続パラメータ

```cpp
// ESP32 アドバタイジング設定
pAdvertising->setMinPreferred(0x06);  // 最小接続間隔: 7.5ms × 6 = 45ms
pAdvertising->setMinPreferred(0x12);  // 最大接続間隔: 7.5ms × 18 = 135ms
```

### 8.3 遅延見積もり

| 処理 | 推定遅延 |
|:-----|:---------|
| アプリ判定 | ~30 ms |
| BLE送信 | ~50 ms |
| ESP32処理 | ~5 ms |
| **合計** | **~85 ms** |

### 8.4 同期マージン

動画連動機能では、遅延を考慮して早めにコマンドを発火する:

```kotlin
const val SYNC_MARGIN_MS = 50  // 50ms先行発火

fun onTimeUpdate(currentTimeMs: Int) {
    triggers.forEach { trigger ->
        if (!trigger.fired && 
            currentTimeMs >= trigger.timeMs - SYNC_MARGIN_MS) {
            sendCommand(trigger.command)
            trigger.fired = true
        }
    }
}
```

---

## 9. 複数デバイス管理

### 9.1 デバイス識別

#### デバイス名規則

```
4D_[タイプ]_[MACアドレス下4桁]
```

| パターン | 説明 | 例 |
|:---------|:-----|:---|
| `4D_LED1_XXXX` | LED1専用デバイス | 4D_LED1_A1B2 |
| `4D_LED2_XXXX` | LED2専用デバイス | 4D_LED2_C3D4 |
| `4D_Device_XXXX` | 統合デバイス | 4D_Device_E5F6 |

#### デバイス名フィルタ

```kotlin
val DEVICE_NAME_PATTERN = Regex("4D_(Device|LED1|LED2)_[0-9A-Fa-f]{4}")

fun isValidDevice(name: String): Boolean {
    return DEVICE_NAME_PATTERN.matches(name)
}
```

### 9.2 接続管理

```kotlin
// 複数接続の管理
private val gattMap = mutableMapOf<String, BluetoothGatt>()
private val commandCharacteristics = mutableMapOf<String, BluetoothGattCharacteristic>()

// 接続状態のStateFlow
private val _connections = MutableStateFlow<Map<String, BleConnection>>(emptyMap())
val connections: StateFlow<Map<String, BleConnection>> = _connections.asStateFlow()
```

### 9.3 エフェクトルーティング

```kotlin
// マッピング例
// LED1エフェクト → 4D_LED1_A1B2 (MACアドレス: XX:XX:XX:XX:A1:B2)
// LED2エフェクト → 4D_LED2_C3D4 (MACアドレス: XX:XX:XX:XX:C3:D4)

suspend fun routeEvent(event: EffectEvent): RouteResult {
    // 1. エフェクトタイプからマッピング取得
    val mapping = mappingRepository.getMapping(event.effect)
    val deviceAddress = mapping.deviceAddress ?: return RouteResult.NotMapped
    
    // 2. コマンド解決
    val command = EffectCommandResolver.resolve(event) ?: return RouteResult.CommandNotFound
    
    // 3. デバイスにコマンド送信
    return bleDeviceManager.sendCommand(deviceAddress, command)
}
```

### 9.4 BLE同時接続制限

| 項目 | 制限 |
|:-----|:-----|
| Android Central同時接続数 | 最大7台（デバイス依存） |
| ESP32 Peripheral接続数 | 1台（本ファームウェア） |
| 推奨同時接続数 | 2-4台 |

---

## 10. セキュリティ

### 10.1 現在の実装

| 項目 | 実装状況 |
|:-----|:---------|
| ペアリング | なし（オープン接続） |
| 暗号化 | なし |
| 認証 | なし |
| アクセス制御 | デバイス名パターンによるフィルタのみ |

### 10.2 セキュリティ考慮事項

**現在のリスク:**

1. 任意のBLEクライアントから接続可能
2. コマンドの改ざん可能
3. 中間者攻撃の可能性

**推奨対策（将来実装）:**

1. BLEペアリングの実装
2. Just Works / Passkey Entry の導入
3. 暗号化通信の有効化
4. デバイス固有のトークン認証

### 10.3 物理的セキュリティ

- ESP32は信頼できる環境でのみ使用
- シリアルポートへのアクセス制限
- ファームウェアの署名検証（将来）

---

## 付録

### A. UUID一覧

| 用途 | UUID |
|:-----|:-----|
| 4DX Effect Service | `4D580001-0000-1000-8000-00805F9B34FB` |
| Command Characteristic | `4D580002-0000-1000-8000-00805F9B34FB` |
| Status Characteristic | `4D580003-0000-1000-8000-00805F9B34FB` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |

### B. コマンド一覧

| カテゴリ | コマンド | 説明 |
|:---------|:---------|:-----|
| LED1 | L1_ON | 点灯 |
| LED1 | L1_OFF | 消灯 |
| LED1 | L1_BL | 点滅 |
| LED2 | L2_ON | 点灯 |
| LED2 | L2_OFF | 消灯 |
| LED2 | L2_BL | 点滅 |
| Vibration | V_UW～V_HB | 各種振動モード |
| Vibration | V_OFF | 停止 |
| Flash | F_ON, F_SB, F_FB | 光モード |
| Flash | F_OFF | 停止 |
| Wind | W_ON | 風ON |
| Wind | W_OFF | 風OFF |
| Water | A_BT | 水噴射 |
| Color | C_RD～C_PP | 各色 |
| Color | C_OFF | 消灯 |

### C. ステータス一覧

| ステータス | 意味 |
|:-----------|:-----|
| CONNECTED | 接続成功 |
| LED1:ON | LED1点灯 |
| LED1:OFF | LED1消灯 |
| LED1:BLINK | LED1点滅 |
| LED2:ON | LED2点灯 |
| LED2:OFF | LED2消灯 |
| LED2:BLINK | LED2点滅 |
| ERR:UNKNOWN | 不明なコマンド |

---

**更新履歴**

| バージョン | 日付 | 変更内容 |
|:-----------|:-----|:---------|
| 1.0.0 | 2026-01-30 | 初版作成 |
