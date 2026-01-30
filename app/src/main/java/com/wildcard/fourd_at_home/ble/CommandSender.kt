package com.wildcard.fourd_at_home.ble

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * コマンド送信ユーティリティ（JSON_SPECIFICATION.md準拠）
 * 各エフェクトデバイスへの文字列ベースコマンド送信
 * 
 * コマンド形式:
 * - EffectStation: "FAN,0/1", "LED,colorId,brightness,effect,transition", "SPLASH", "MIST,0/1/2"
 * - ActionDrive: "MOTOR,mode_name" (up_weak, down_strong, etc.)
 */
@Singleton
class CommandSender @Inject constructor(
    private val deviceManager: BleDeviceManager
) {
    companion object {
        private const val TAG = "CommandSender"
    }

    // ===============================
    // EffectStation コマンド（文字列ベース）
    // ===============================

    /**
     * ファンを制御
     * @param on true=ON, false=OFF
     */
    suspend fun sendFanCommand(on: Boolean): Result<Unit> {
        val command = "FAN,${if (on) 1 else 0}"
        return sendStringToEffectStation(command)
    }

    /**
     * 水噴射（ワンショット）
     */
    suspend fun sendSplashCommand(): Result<Unit> {
        return sendStringToEffectStation("SPLASH")
    }

    /**
     * ミストを制御
     * @param mode 0=OFF, 1=一瞬(shot), 2=継続(start)
     */
    suspend fun sendMistCommand(mode: Int): Result<Unit> {
        val command = "MIST,${mode.coerceIn(0, 2)}"
        return sendStringToEffectStation(command)
    }

    /**
     * LEDを色IDで制御（4DHOME_STATION_CONTROL.ino準拠）
     * @param colorId 0-11 (0=PINK, 1=RED, 2=ORANGE, 3=YELLOW, 4=YELLOW_GREEN,
     *                      5=GREEN, 6=DARK_GREEN, 7=CYAN, 8=BLUE, 9=PURPLE, 10=WHITE, 11=OFF)
     * @param brightness 0-2 (0=なし/OFF, 1=弱, 2=強)
     * @param effect 0=点灯, 1=点滅, 2=呼吸
     * @param transition 0=一瞬, 1=フェード
     */
    suspend fun sendLedColorCommand(
        colorId: Int, 
        brightness: Int = 2, 
        effect: Int = 0, 
        transition: Int = 0
    ): Result<Unit> {
        val command = "LED,${colorId.coerceIn(0, 11)},${brightness.coerceIn(0, 2)},${effect.coerceIn(0, 2)},${transition.coerceIn(0, 1)}"
        return sendStringToEffectStation(command)
    }

    /**
     * LEDをRGB値で制御（互換性のため維持）
     * 内部でcolorIdにマッピング
     */
    suspend fun sendLedCommand(r: Int, g: Int, b: Int, brightness: Int = 255): Result<Unit> {
        // RGBを最も近い色IDにマッピング
        val colorId = mapRgbToColorId(r, g, b)
        val brightnessLevel = when {
            brightness <= 85 -> 0   // 弱
            brightness <= 170 -> 1  // 中
            else -> 2               // 強
        }
        return sendLedColorCommand(colorId, brightnessLevel, 0, 0)
    }

    /**
     * RGBを色IDにマッピング
     */
    private fun mapRgbToColorId(r: Int, g: Int, b: Int): Int {
        // 消灯チェック
        if (r == 0 && g == 0 && b == 0) return 11  // OFF
        
        // 簡易マッピング（最も近い色を選択）
        return when {
            r > 200 && g < 100 && b < 100 -> 1    // RED
            r > 200 && g > 100 && b < 100 -> 3    // ORANGE
            r > 200 && g > 200 && b < 100 -> 4    // YELLOW
            r < 100 && g > 200 && b < 100 -> 5    // GREEN
            r < 100 && g < 100 && b > 200 -> 7    // BLUE
            r > 150 && g < 100 && b > 150 -> 9    // PURPLE
            r > 200 && g > 200 && b > 200 -> 10   // WHITE
            r > 200 && g < 150 && b > 150 -> 0    // PINK
            r < 100 && g > 150 && b > 200 -> 6    // CYAN
            r < 50 && g > 50 && b > 100 -> 8      // INDIGO
            r > 150 && g > 200 && b < 50 -> 2     // LIME
            else -> 10  // デフォルトはWHITE
        }
    }

    /**
     * EffectStationの全エフェクトをOFF
     */
    suspend fun sendEffectStationAllOff(): Result<Unit> {
        val results = mutableListOf<Result<Unit>>()
        results.add(sendFanCommand(false))
        results.add(sendMistCommand(0))
        results.add(sendLedColorCommand(11, 0, 0, 0))  // LED OFF
        
        return if (results.all { it.isSuccess }) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("EffectStation全停止の一部が失敗しました"))
        }
    }

    /**
     * EffectStationに文字列コマンドを送信
     */
    private suspend fun sendStringToEffectStation(command: String): Result<Unit> {
        val device = deviceManager.getDeviceByType(DeviceType.EFFECT_STATION)
        if (device == null) {
            Log.w(TAG, "EffectStationが接続されていません")
            return Result.failure(Exception("EffectStationが接続されていません"))
        }
        Log.d(TAG, "EffectStation送信: $command")
        return deviceManager.sendCommand(device.address, command.toByteArray(Charsets.UTF_8))
    }

    // ===============================
    // ActionDrive コマンド（文字列ベース）
    // ===============================

    /**
     * Motor1に振動モードを送信
     * @param mode VibrationModeの名前（up_weak, down_strong, heartbeat等）
     */
    suspend fun sendMotor1StringCommand(mode: String): Result<Unit> {
        val command = "MOTOR,$mode"
        return sendStringToActionDrive1(command)
    }

    /**
     * Motor2に振動モードを送信
     */
    suspend fun sendMotor2StringCommand(mode: String): Result<Unit> {
        val command = "MOTOR,$mode"
        return sendStringToActionDrive2(command)
    }

    /**
     * Motor1(振動1)を制御（互換性のため維持）
     * @param intensity 0-255 (0=OFF, 255=MAX)
     */
    suspend fun sendMotor1Command(intensity: Int): Result<Unit> {
        val mode = when {
            intensity == 0 -> "OFF"
            intensity < 85 -> "up_weak"
            intensity < 170 -> "up"
            else -> "up_strong"
        }
        return sendMotor1StringCommand(mode)
    }

    /**
     * Motor2(振動2)を制御（互換性のため維持）
     */
    suspend fun sendMotor2Command(intensity: Int): Result<Unit> {
        val mode = when {
            intensity == 0 -> "OFF"
            intensity < 85 -> "down_weak"
            intensity < 170 -> "down"
            else -> "down_strong"
        }
        return sendMotor2StringCommand(mode)
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
        val result1 = sendMotor1StringCommand("OFF")
        val result2 = sendMotor2StringCommand("OFF")
        
        return if (result1.isSuccess && result2.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("モーター停止に一部失敗しました"))
        }
    }

    /**
     * ActionDrive Motor1に文字列コマンドを送信
     */
    private suspend fun sendStringToActionDrive1(command: String): Result<Unit> {
        val device = deviceManager.getDeviceByType(DeviceType.ACTION_DRIVE_1)
        if (device == null) {
            Log.w(TAG, "ActionDrive Motor1が接続されていません")
            return Result.failure(Exception("ActionDrive Motor1が接続されていません"))
        }
        Log.d(TAG, "ActionDrive1送信: $command")
        return deviceManager.sendCommand(device.address, command.toByteArray(Charsets.UTF_8))
    }

    /**
     * ActionDrive Motor2に文字列コマンドを送信
     */
    private suspend fun sendStringToActionDrive2(command: String): Result<Unit> {
        val device = deviceManager.getDeviceByType(DeviceType.ACTION_DRIVE_2)
        if (device == null) {
            Log.w(TAG, "ActionDrive Motor2が接続されていません")
            return Result.failure(Exception("ActionDrive Motor2が接続されていません"))
        }
        Log.d(TAG, "ActionDrive2送信: $command")
        return deviceManager.sendCommand(device.address, command.toByteArray(Charsets.UTF_8))
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
}
