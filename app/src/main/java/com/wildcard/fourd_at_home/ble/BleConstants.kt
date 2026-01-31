package com.wildcard.fourd_at_home.ble

import java.util.UUID

object BleConstants {
    // === Service/Characteristic UUIDs ===
    val SERVICE_UUID: UUID = UUID.fromString("4D580001-0000-1000-8000-00805F9B34FB")
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("4D580002-0000-1000-8000-00805F9B34FB")
    val STATUS_CHAR_UUID: UUID = UUID.fromString("4D580003-0000-1000-8000-00805F9B34FB")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // === デバイス名パターン ===
    // 4D_ES_XXXX  : EffectStation
    // 4D_AD1_XXXX : ActionDrive Motor1
    // 4D_AD2_XXXX : ActionDrive Motor2
    // 4D_で始まるすべてのデバイスを受け入れる
    val DEVICE_NAME_PATTERN = Regex("4D_[A-Za-z0-9]+_[0-9A-Fa-f]{4}")

    // デバイスタイプ判定用プレフィックス
    const val PREFIX_EFFECT_STATION = "4D_ES_"
    const val PREFIX_ACTION_DRIVE_1 = "4D_AD1_"
    const val PREFIX_ACTION_DRIVE_2 = "4D_AD2_"

    // === タイムアウト設定 (ms) ===
    const val SCAN_TIMEOUT_MS = 15_000L
    const val CONNECTION_TIMEOUT_MS = 10_000L
    const val WRITE_TIMEOUT_MS = 5_000L
    // BLE書き込み間の遅延（ESP32の処理時間を考慮）
    // 250msイベント間隔に対応するため、複数デバイスへの送信が完了するよう調整
    const val GATT_OPERATION_DELAY_MS = 30L
    // 同一デバイスへの連続コマンド間隔
    const val SAME_DEVICE_COMMAND_DELAY_MS = 20L

    // === 再接続設定 ===
    const val MAX_RECONNECT_ATTEMPTS = 3
    const val RECONNECT_DELAY_MS = 2_000L
}
