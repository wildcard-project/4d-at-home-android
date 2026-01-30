package com.wildcard.fourd_at_home.ble

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * コマンド送信ユーティリティ
 * 各エフェクトデバイスへのコマンド送信を抽象化
 */
@Singleton
class CommandSender @Inject constructor(
    private val deviceManager: BleDeviceManager
) {
    companion object {
        private const val TAG = "CommandSender"

        // === EffectStation コマンド ===
        // コマンド形式: [CMD_TYPE, VALUE, ...]
        const val CMD_FAN = 0x01.toByte()
        const val CMD_WATER = 0x02.toByte()
        const val CMD_MIST = 0x03.toByte()
        const val CMD_LED = 0x04.toByte()
        const val CMD_ALL_OFF = 0xFF.toByte()

        // === ActionDrive コマンド ===
        const val CMD_VIBRATION = 0x10.toByte()
    }

    // ===============================
    // EffectStation コマンド
    // ===============================

    /**
     * ファンを制御
     * @param intensity 0-255 (0=OFF, 255=MAX)
     */
    suspend fun sendFanCommand(intensity: Int): Result<Unit> {
        return sendToEffectStation(byteArrayOf(CMD_FAN, intensity.coerceIn(0, 255).toByte()))
    }

    /**
     * 水噴射を制御
     * @param intensity 0-255 (0=OFF, 255=MAX)
     */
    suspend fun sendWaterCommand(intensity: Int): Result<Unit> {
        return sendToEffectStation(byteArrayOf(CMD_WATER, intensity.coerceIn(0, 255).toByte()))
    }

    /**
     * ミストを制御
     * @param intensity 0-255 (0=OFF, 255=MAX)
     */
    suspend fun sendMistCommand(intensity: Int): Result<Unit> {
        return sendToEffectStation(byteArrayOf(CMD_MIST, intensity.coerceIn(0, 255).toByte()))
    }

    /**
     * LEDを制御
     * @param r Red 0-255
     * @param g Green 0-255
     * @param b Blue 0-255
     * @param brightness 明るさ 0-255
     */
    suspend fun sendLedCommand(r: Int, g: Int, b: Int, brightness: Int = 255): Result<Unit> {
        return sendToEffectStation(
            byteArrayOf(
                CMD_LED,
                r.coerceIn(0, 255).toByte(),
                g.coerceIn(0, 255).toByte(),
                b.coerceIn(0, 255).toByte(),
                brightness.coerceIn(0, 255).toByte()
            )
        )
    }

    /**
     * EffectStationの全エフェクトをOFF
     */
    suspend fun sendEffectStationAllOff(): Result<Unit> {
        return sendToEffectStation(byteArrayOf(CMD_ALL_OFF))
    }

    /**
     * EffectStationにコマンドを送信
     */
    private suspend fun sendToEffectStation(command: ByteArray): Result<Unit> {
        val device = deviceManager.getDeviceByType(DeviceType.EFFECT_STATION)
        if (device == null) {
            Log.w(TAG, "EffectStationが接続されていません")
            return Result.failure(Exception("EffectStationが接続されていません"))
        }
        return deviceManager.sendCommand(device.address, command)
    }

    // ===============================
    // ActionDrive コマンド
    // ===============================

    /**
     * Motor1(振動1)を制御
     * @param intensity 0-255 (0=OFF, 255=MAX)
     */
    suspend fun sendMotor1Command(intensity: Int): Result<Unit> {
        return sendToActionDrive1(byteArrayOf(CMD_VIBRATION, intensity.coerceIn(0, 255).toByte()))
    }

    /**
     * Motor2(振動2)を制御
     * @param intensity 0-255 (0=OFF, 255=MAX)
     */
    suspend fun sendMotor2Command(intensity: Int): Result<Unit> {
        return sendToActionDrive2(byteArrayOf(CMD_VIBRATION, intensity.coerceIn(0, 255).toByte()))
    }

    /**
     * 両方のモーターを制御
     */
    suspend fun sendBothMotorsCommand(intensity: Int): Result<Unit> {
        val result1 = sendMotor1Command(intensity)
        val result2 = sendMotor2Command(intensity)
        
        return if (result1.isSuccess && result2.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("モーターコマンド送信に一部失敗しました"))
        }
    }

    /**
     * 全モーターをOFF
     */
    suspend fun sendAllMotorsOff(): Result<Unit> {
        return sendBothMotorsCommand(0)
    }

    /**
     * ActionDrive Motor1にコマンドを送信
     */
    private suspend fun sendToActionDrive1(command: ByteArray): Result<Unit> {
        val device = deviceManager.getDeviceByType(DeviceType.ACTION_DRIVE_1)
        if (device == null) {
            Log.w(TAG, "ActionDrive Motor1が接続されていません")
            return Result.failure(Exception("ActionDrive Motor1が接続されていません"))
        }
        return deviceManager.sendCommand(device.address, command)
    }

    /**
     * ActionDrive Motor2にコマンドを送信
     */
    private suspend fun sendToActionDrive2(command: ByteArray): Result<Unit> {
        val device = deviceManager.getDeviceByType(DeviceType.ACTION_DRIVE_2)
        if (device == null) {
            Log.w(TAG, "ActionDrive Motor2が接続されていません")
            return Result.failure(Exception("ActionDrive Motor2が接続されていません"))
        }
        return deviceManager.sendCommand(device.address, command)
    }

    // ===============================
    // 統合コマンド
    // ===============================

    /**
     * 全デバイスの全エフェクトをOFF
     */
    suspend fun sendAllDevicesOff(): Result<Unit> {
        val results = mutableListOf<Result<Unit>>()

        // EffectStation
        if (deviceManager.getDeviceByType(DeviceType.EFFECT_STATION) != null) {
            results.add(sendEffectStationAllOff())
        }

        // ActionDrive
        if (deviceManager.getDeviceByType(DeviceType.ACTION_DRIVE_1) != null ||
            deviceManager.getDeviceByType(DeviceType.ACTION_DRIVE_2) != null
        ) {
            results.add(sendAllMotorsOff())
        }

        return if (results.all { it.isSuccess }) {
            Result.success(Unit)
        } else if (results.isEmpty()) {
            Result.failure(Exception("接続されているデバイスがありません"))
        } else {
            Result.failure(Exception("一部のデバイスでエフェクト停止に失敗しました"))
        }
    }

    /**
     * タイムラインイベントを送信
     * JSONタイムラインからのイベントを各デバイスに送信
     */
    suspend fun sendTimelineEvent(event: TimelineEvent): Result<Unit> {
        return when (event) {
            is TimelineEvent.Fan -> sendFanCommand(event.intensity)
            is TimelineEvent.Water -> sendWaterCommand(event.intensity)
            is TimelineEvent.Mist -> sendMistCommand(event.intensity)
            is TimelineEvent.Led -> sendLedCommand(event.r, event.g, event.b, event.brightness)
            is TimelineEvent.Vibration -> when (event.motor) {
                1 -> sendMotor1Command(event.intensity)
                2 -> sendMotor2Command(event.intensity)
                else -> sendBothMotorsCommand(event.intensity)
            }
            is TimelineEvent.AllOff -> sendAllDevicesOff()
        }
    }
}

/**
 * タイムラインイベント
 * JSONタイムラインから解析されるイベント
 */
sealed class TimelineEvent {
    abstract val timestampMs: Long

    data class Fan(
        override val timestampMs: Long,
        val intensity: Int
    ) : TimelineEvent()

    data class Water(
        override val timestampMs: Long,
        val intensity: Int
    ) : TimelineEvent()

    data class Mist(
        override val timestampMs: Long,
        val intensity: Int
    ) : TimelineEvent()

    data class Led(
        override val timestampMs: Long,
        val r: Int,
        val g: Int,
        val b: Int,
        val brightness: Int = 255
    ) : TimelineEvent()

    data class Vibration(
        override val timestampMs: Long,
        val intensity: Int,
        val motor: Int = 0  // 0=both, 1=motor1, 2=motor2
    ) : TimelineEvent()

    data class AllOff(
        override val timestampMs: Long
    ) : TimelineEvent()
}
