# 4D@HOME Android 詳細仕様書 - BLE通信プロトコル仕様

**バージョン**: 1.0.0  
**作成日**: 2025年1月30日  
**対象**: Bluetooth Low Energy通信

---

## 📑 目次

1. [BLE概要](#1-ble概要)
2. [UUID定義](#2-uuid定義)
3. [デバイス識別](#3-デバイス識別)
4. [接続フロー](#4-接続フロー)
5. [コマンド形式](#5-コマンド形式)
6. [ステータス通知](#6-ステータス通知)
7. [エラー処理](#7-エラー処理)
8. [タイムアウト設定](#8-タイムアウト設定)

---

## 1. BLE概要

### 1.1 通信構成

```
┌─────────────────────────────────────────────────────────────┐
│                    Android (Central)                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │               GATT Client                              │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────┬───────────────────────────────────┘
                           │ BLE Connection (GATT)
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ EffectStation │  │ Motor1        │  │ Motor2        │
│  (Peripheral) │  │  (Peripheral) │  │  (Peripheral) │
│               │  │               │  │               │
│ GATT Server   │  │ GATT Server   │  │ GATT Server   │
│ ├─ Service    │  │ ├─ Service    │  │ ├─ Service    │
│ │  ├─ Command │  │ │  ├─ Command │  │ │  ├─ Command │
│ │  └─ Status  │  │ │  └─ Status  │  │ │  └─ Status  │
└───────────────┘  └───────────────┘  └───────────────┘
```

### 1.2 通信特性

| 項目 | 値 |
|------|-----|
| **BLEバージョン** | 4.2以上 |
| **通信距離** | 約10m（屋内環境） |
| **MTUサイズ** | デフォルト23バイト |
| **接続数** | 最大3台同時 |
| **書き込み方式** | Write Request (応答あり) |
| **通知方式** | Notification (CCCDで有効化) |

---

## 2. UUID定義

### 2.1 サービスUUID

```
SERVICE_UUID: 4D580001-0000-1000-8000-00805F9B34FB
```

**構成**:
- `4D58` = "4DX"のASCII表現を意識
- `0001` = Service識別子
- Base UUID: `00000000-0000-1000-8000-00805F9B34FB` (Bluetooth SIG Base)

### 2.2 キャラクタリスティックUUID

| Characteristic | UUID | プロパティ | 説明 |
|----------------|------|-----------|------|
| **Command** | `4D580002-0000-1000-8000-00805F9B34FB` | Write | コマンド送信用 |
| **Status** | `4D580003-0000-1000-8000-00805F9B34FB` | Notify | ステータス通知用 |

### 2.3 ディスクリプタUUID

| Descriptor | UUID | 説明 |
|------------|------|------|
| **CCCD** | `00002902-0000-1000-8000-00805f9b34fb` | Client Characteristic Configuration Descriptor |

### 2.4 Kotlinでの定義

```kotlin
// BleConstants.kt
object BleConstants {
    // Service/Characteristic UUIDs
    val SERVICE_UUID: UUID = 
        UUID.fromString("4D580001-0000-1000-8000-00805F9B34FB")
    val COMMAND_CHAR_UUID: UUID = 
        UUID.fromString("4D580002-0000-1000-8000-00805F9B34FB")
    val STATUS_CHAR_UUID: UUID = 
        UUID.fromString("4D580003-0000-1000-8000-00805F9B34FB")
    val CCCD_UUID: UUID = 
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
```

---

## 3. デバイス識別

### 3.1 デバイス名パターン

```
デバイス名形式: 4D_[TYPE]_[MAC下4桁]

例:
- 4D_ES_A1B2   (EffectStation)
- 4D_AD1_C3D4  (ActionDrive Motor1)
- 4D_AD2_E5F6  (ActionDrive Motor2)
```

### 3.2 デバイスタイプ判定

| プレフィックス | デバイスタイプ | 説明 |
|---------------|---------------|------|
| `4D_ES_` | EffectStation | 環境エフェクト（風・水・LED） |
| `4D_AD1_` | ActionDrive Motor1 | 振動モーター1（背中/前方） |
| `4D_AD2_` | ActionDrive Motor2 | 振動モーター2（お尻/後方） |

### 3.3 正規表現パターン

```kotlin
val DEVICE_NAME_PATTERN = Regex("4D_[A-Za-z0-9]+_[0-9A-Fa-f]{4}")
```

### 3.4 デバイスタイプEnum

```kotlin
enum class DeviceType(val prefix: String, val displayName: String) {
    EFFECT_STATION("4D_ES_", "EffectStation"),
    ACTION_DRIVE_1("4D_AD1_", "ActionDrive Motor1"),
    ACTION_DRIVE_2("4D_AD2_", "ActionDrive Motor2"),
    UNKNOWN("", "不明");

    companion object {
        fun fromDeviceName(name: String?): DeviceType {
            if (name == null) return UNKNOWN
            return entries.find { name.startsWith(it.prefix) } ?: UNKNOWN
        }
    }
}
```

### 3.5 デバイス名生成（ESP32側）

```cpp
// ESP32ファームウェアでのデバイス名生成
String getDeviceName() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    char name[16];
    
    // デバイスタイプに応じて変更
    sprintf(name, "4D_ES_%02X%02X", mac[4], mac[5]);   // EffectStation
    // sprintf(name, "4D_AD1_%02X%02X", mac[4], mac[5]); // Motor1
    // sprintf(name, "4D_AD2_%02X%02X", mac[4], mac[5]); // Motor2
    
    return String(name);
}
```

---

## 4. 接続フロー

### 4.1 接続シーケンス図

```
Android (Central)              ESP32 (Peripheral)
     │                              │
     │──── Scan Request ──────────▶│
     │◀─── Advertisement ──────────│ (4D_ES_XXXX)
     │                              │
     │──── Connect Request ───────▶│
     │◀─── Connect Response ───────│
     │                              │
     │                        [STATE_CONNECTED]
     │                              │
     │──── Discover Services ─────▶│
     │◀─── Services Discovered ────│
     │                              │
     │     [Service: 4D580001...]   │
     │     ├─ Char: 4D580002 (Cmd)  │
     │     └─ Char: 4D580003 (Sts)  │
     │                              │
     │──── Enable Notification ───▶│ (CCCD Write)
     │◀─── Write Response ─────────│
     │                              │
     │                        [STATE_READY]
     │                              │
     │──── Write Command ─────────▶│ ("FAN,1")
     │◀─── Write Response ─────────│
     │                              │
     │◀─── Status Notification ────│ ([0x01,0x00,...])
```

### 4.2 接続状態遷移

```
                    ┌─────────────────┐
                    │  DISCONNECTED   │
                    └────────┬────────┘
                             │ connect()
                             ▼
                    ┌─────────────────┐
                    │   CONNECTING    │
                    └────────┬────────┘
                             │ onConnectionStateChange(CONNECTED)
                             ▼
                    ┌─────────────────┐
                    │    CONNECTED    │
                    └────────┬────────┘
                             │ discoverServices()
                             ▼
             ┌───────────────────────────────┐
             │     DISCOVERING_SERVICES      │
             └───────────────┬───────────────┘
                             │ onServicesDiscovered()
                             ▼
                    ┌─────────────────┐
              ┌────▶│      READY      │◀────┐
              │     └────────┬────────┘     │
              │              │              │
              │   disconnect()│  再接続成功  │
              │              ▼              │
              │     ┌─────────────────┐     │
              │     │  DISCONNECTING  │     │
              │     └────────┬────────┘     │
              │              │              │
              │              ▼              │
              │     ┌─────────────────┐     │
              └─────│  DISCONNECTED   │─────┘
                    └────────┬────────┘
                             │ エラー発生
                             ▼
                    ┌─────────────────┐
                    │      ERROR      │
                    └─────────────────┘
```

### 4.3 接続状態Enum

```kotlin
enum class ConnectionState {
    DISCONNECTED,          // 未接続
    CONNECTING,            // 接続中
    CONNECTED,             // 接続済み（サービス検出前）
    DISCOVERING_SERVICES,  // サービス検出中
    READY,                 // 準備完了（コマンド送信可能）
    DISCONNECTING,         // 切断中
    ERROR                  // エラー
}
```

---

## 5. コマンド形式

### 5.1 コマンド概要

**通信方式**: 文字列ベース（UTF-8）のシンプルなテキストコマンド

```
[コマンドタイプ],[パラメータ1],[パラメータ2],...
```

### 5.2 EffectStation コマンド

#### FAN（ファン制御）

```
コマンド: FAN,[on/off]
パラメータ:
  - 0: OFF
  - 1: ON

例:
  FAN,1  → ファンON
  FAN,0  → ファンOFF
```

#### SPLASH（水噴射）

```
コマンド: SPLASH
パラメータ: なし
動作: 200ms間水を噴射（ワンショット）

例:
  SPLASH
```

#### MIST（ミスト）

```
コマンド: MIST,[mode]
パラメータ:
  - 0: OFF
  - 1: SHOT（一瞬噴射 100ms）
  - 2: START（継続）

例:
  MIST,0  → ミストOFF
  MIST,1  → 一瞬噴射
  MIST,2  → 継続噴射
```

#### LED（LED制御）

```
コマンド: LED,[colorId],[brightness],[effect],[transition]
パラメータ:
  - colorId: 0-11（色ID、下表参照）
  - brightness: 0-2（0=OFF, 1=弱, 2=強）
  - effect: 0-2（0=点灯, 1=点滅, 2=呼吸）
  - transition: 0-1（0=一瞬, 1=フェード）

例:
  LED,1,2,0,0   → 赤、強、点灯、一瞬
  LED,7,2,1,0   → シアン、強、点滅、一瞬
  LED,11,0,0,0  → 消灯
```

**色IDテーブル**:

| colorId | 色名 | RGB値 |
|---------|------|-------|
| 0 | PINK | (255, 20, 100) |
| 1 | RED | (255, 0, 0) |
| 2 | ORANGE | (255, 100, 0) |
| 3 | YELLOW | (255, 255, 0) |
| 4 | YELLOW_GREEN | (150, 255, 0) |
| 5 | GREEN | (0, 255, 0) |
| 6 | DARK_GREEN | (0, 100, 0) |
| 7 | CYAN | (0, 255, 255) |
| 8 | BLUE | (0, 0, 255) |
| 9 | PURPLE | (150, 0, 255) |
| 10 | WHITE | Wチャンネル使用 |
| 11 | OFF | 消灯 |

### 5.3 ActionDrive コマンド

#### MOTOR（振動制御）

```
コマンド: MOTOR,[mode]
パラメータ (mode):
  - OFF: 停止
  - WEAK: 弱振動
  - MEDIUM_WEAK: 中弱振動
  - MEDIUM_STRONG: 中強振動
  - STRONG: 強振動
  - HEARTBEAT: 心拍パターン
  - RUMBLE_FAST: 速いランブル
  - RUMBLE_SLOW: 遅いランブル

例:
  MOTOR,STRONG       → 強振動
  MOTOR,HEARTBEAT    → 心拍パターン
  MOTOR,OFF          → 停止
```

**振動強度とピン対応**:

| モード | D5 (強) | D6 (中強) | D7 (中弱) | D8 (弱) |
|--------|---------|-----------|-----------|---------|
| WEAK | OFF | OFF | OFF | ON |
| MEDIUM_WEAK | OFF | OFF | ON | OFF |
| MEDIUM_STRONG | OFF | ON | ON | OFF |
| STRONG | ON | ON | ON | ON |

### 5.4 共通コマンド

```
OFF / STOP / ALL_OFF
  → 全エフェクト停止
```

---

## 6. ステータス通知

### 6.1 EffectStation ステータス

```
ステータス形式: 8バイトバイナリ

[0]: FAN状態 (0=OFF, 1=ON)
[1]: SPLASH状態 (0=OFF, 1=アクティブ)
[2]: MIST状態 (0=OFF, 1=SHOT中, 2=継続中)
[3]: LED colorId (0-11)
[4]: LED brightness (0-2)
[5]: LED effect (0-2)
[6]: 予約
[7]: 予約
```

### 6.2 ActionDrive ステータス

```
ステータス形式: 4バイトバイナリ

[0]: デバイス識別子 (0x01=Motor1, 0x02=Motor2)
[1]: モーターモード (0-7)
[2]: パターン実行中フラグ (0/1)
[3]: 予約
```

### 6.3 Notification有効化

```kotlin
private fun enableNotifications(
    gatt: BluetoothGatt, 
    characteristic: BluetoothGattCharacteristic
) {
    try {
        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor, 
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    } catch (e: SecurityException) {
        // エラー処理
    }
}
```

---

## 7. エラー処理

### 7.1 BLEエラー定義

```kotlin
sealed class BleError(open val message: String) {
    data class BluetoothDisabled(
        override val message: String = "Bluetoothが無効です"
    ) : BleError(message)

    data class PermissionDenied(
        override val message: String = "Bluetooth権限がありません"
    ) : BleError(message)

    data class ScanFailed(
        override val message: String,
        val errorCode: Int
    ) : BleError(message)

    data class ConnectionFailed(
        override val message: String,
        val statusCode: Int? = null
    ) : BleError(message)

    data class ServiceDiscoveryFailed(
        override val message: String = "サービスが見つかりません"
    ) : BleError(message)

    data class CharacteristicNotFound(
        override val message: String = "Characteristicが見つかりません"
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

### 7.2 スキャンエラーコード

```kotlin
private fun getScanFailureReason(errorCode: Int): String {
    return when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> 
            "既にスキャン中です"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> 
            "アプリ登録に失敗しました"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> 
            "この機能はサポートされていません"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> 
            "内部エラーが発生しました"
        else -> 
            "不明なエラー (code: $errorCode)"
    }
}
```

### 7.3 自動再接続

```kotlin
private fun startReconnection(device: ScannedDevice) {
    val address = device.address
    
    reconnectJobs[address] = scope.launch {
        repeat(BleConstants.MAX_RECONNECT_ATTEMPTS) { attempt ->
            Log.d(TAG, "再接続試行 ${attempt + 1}/${BleConstants.MAX_RECONNECT_ATTEMPTS}")
            delay(BleConstants.RECONNECT_DELAY_MS)

            val result = connect(device)
            if (result.isSuccess) {
                Log.d(TAG, "再接続成功: $address")
                return@launch
            }
        }
        Log.w(TAG, "再接続失敗 (最大試行回数に達しました)")
    }
}
```

---

## 8. タイムアウト設定

### 8.1 定数定義

```kotlin
object BleConstants {
    // タイムアウト設定 (ms)
    const val SCAN_TIMEOUT_MS = 15_000L        // スキャンタイムアウト
    const val CONNECTION_TIMEOUT_MS = 10_000L   // 接続タイムアウト
    const val WRITE_TIMEOUT_MS = 5_000L         // 書き込みタイムアウト
    const val GATT_OPERATION_DELAY_MS = 100L    // GATT操作間ディレイ

    // 再接続設定
    const val MAX_RECONNECT_ATTEMPTS = 3        // 最大再接続試行回数
    const val RECONNECT_DELAY_MS = 2_000L       // 再接続間隔
}
```

### 8.2 タイムアウト処理

```kotlin
suspend fun connect(device: ScannedDevice): Result<Unit> {
    return try {
        withTimeout(BleConstants.CONNECTION_TIMEOUT_MS) {
            connectInternal(device)
        }
    } catch (e: TimeoutCancellationException) {
        Result.failure(BleError.Timeout())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## 付録: Android BLE権限

### Android 12+ (API 31+)

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

### Android 11以下

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### 権限チェックコード

```kotlin
fun hasRequiredPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
```
