package com.wildcard.fourd_at_home.ble

import android.bluetooth.BluetoothDevice

/**
 * デバイスタイプ
 */
enum class DeviceType(val prefix: String, val displayName: String) {
    EFFECT_STATION(BleConstants.PREFIX_EFFECT_STATION, "EffectStation"),
    ACTION_DRIVE_1(BleConstants.PREFIX_ACTION_DRIVE_1, "ActionDrive Motor1"),
    ACTION_DRIVE_2(BleConstants.PREFIX_ACTION_DRIVE_2, "ActionDrive Motor2"),
    UNKNOWN("", "不明");

    companion object {
        fun fromDeviceName(name: String?): DeviceType {
            if (name == null) return UNKNOWN
            return entries.find { name.startsWith(it.prefix) } ?: UNKNOWN
        }
    }
}

/**
 * 接続状態
 */
enum class ConnectionState {
    DISCONNECTED,          // 未接続
    CONNECTING,            // 接続中
    CONNECTED,             // 接続済み（サービス検出前）
    DISCOVERING_SERVICES,  // サービス検出中
    READY,                 // 準備完了（コマンド送信可能）
    DISCONNECTING,         // 切断中
    ERROR                  // エラー
}

/**
 * スキャンされたデバイス
 */
data class ScannedDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: DeviceType,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val signalStrength: SignalStrength
        get() = when {
            rssi >= -50 -> SignalStrength.EXCELLENT
            rssi >= -60 -> SignalStrength.GOOD
            rssi >= -70 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }
}

/**
 * 信号強度
 */
enum class SignalStrength(val displayName: String, val emoji: String) {
    EXCELLENT("非常に強い", "📶"),
    GOOD("強い", "📶"),
    FAIR("普通", "📶"),
    WEAK("弱い", "📶")
}

/**
 * BLE接続情報
 */
data class BleConnection(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val deviceType: DeviceType,
    val state: ConnectionState,
    val rssi: Int = 0,
    val errorMessage: String? = null
)

/**
 * コマンドログエントリ
 */
data class CommandLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: CommandDirection,
    val deviceAddress: String,
    val deviceType: DeviceType,
    val command: String,
    val status: CommandStatus,
    val errorMessage: String? = null
)

enum class CommandDirection { TX, RX }

enum class CommandStatus {
    SUCCESS,
    PENDING,
    FAILED,
    TIMEOUT
}

/**
 * BLEエラー
 */
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
