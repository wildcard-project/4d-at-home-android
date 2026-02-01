package com.wildcard.fourd_at_home.playback

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * スマホ本体のエフェクト制御（バイブレーション・フラッシュライト）
 * タイムラインのエフェクトに連動して発火
 */
@Singleton
class PhoneEffectController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PhoneEffectController"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // バイブレーター
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    // カメラマネージャー（フラッシュ用）
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    // フラッシュ対応カメラID
    private var flashCameraId: String? = null
    
    // 現在のフラッシュ状態
    private var isFlashOn = false
    
    // 点滅ジョブ
    private var flashBlinkJob: Job? = null
    
    // バイブレーションジョブ
    private var vibrationJob: Job? = null

    init {
        // フラッシュ対応カメラを検索
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) {
                    flashCameraId = cameraId
                    Log.d(TAG, "フラッシュ対応カメラ発見: $cameraId")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "カメラ検索エラー: ${e.message}")
        }
    }

    /**
     * バイブレーション開始
     * @param intensity 強度（weak, mid_weak, mid_strong, strong）
     * @param isHeartbeat 心拍パターンかどうか
     */
    fun startVibration(intensity: String, isHeartbeat: Boolean = false) {
        vibrationJob?.cancel()
        
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "バイブレーション非対応デバイス")
            return
        }
        
        Log.d(TAG, "バイブレーション開始: intensity=$intensity, heartbeat=$isHeartbeat")
        
        if (isHeartbeat) {
            // 心拍パターン: ドクン...ドクン...
            vibrationJob = scope.launch {
                while (isActive) {
                    vibrateOnce(100, 180)
                    delay(80)
                    vibrateOnce(80, 150)
                    delay(600)
                }
            }
        } else {
            // 強度に応じた連続バイブレーション
            val amplitude = when {
                intensity.contains("strong", ignoreCase = true) -> 255
                intensity.contains("mid", ignoreCase = true) -> 180
                else -> 100
            }
            
            vibrationJob = scope.launch {
                while (isActive) {
                    vibrateOnce(200, amplitude)
                    delay(250)
                }
            }
        }
    }
    
    /**
     * 単発バイブレーション
     */
    private fun vibrateOnce(durationMs: Long, amplitude: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "バイブレーションエラー: ${e.message}")
        }
    }

    /**
     * バイブレーション停止
     */
    fun stopVibration() {
        Log.d(TAG, "バイブレーション停止")
        vibrationJob?.cancel()
        vibrationJob = null
        vibrator.cancel()
    }

    /**
     * フラッシュライト開始
     * @param mode 点灯モード（steady, blink, slow_blink, fast_blink, breath）
     */
    fun startFlash(mode: String) {
        flashBlinkJob?.cancel()
        
        val cameraId = flashCameraId
        if (cameraId == null) {
            Log.w(TAG, "フラッシュライト非対応デバイス")
            return
        }
        
        Log.d(TAG, "フラッシュ開始: mode=$mode")
        
        when {
            mode.contains("fast_blink", ignoreCase = true) -> {
                // 高速点滅（300ms間隔 - カメラAPIの応答時間を考慮）
                flashBlinkJob = scope.launch {
                    while (isActive) {
                        setFlash(true)
                        delay(300)
                        setFlash(false)
                        delay(300)
                    }
                }
            }
            mode.contains("slow_blink", ignoreCase = true) || mode.contains("blink", ignoreCase = true) -> {
                // 通常点滅（800ms間隔）
                flashBlinkJob = scope.launch {
                    while (isActive) {
                        setFlash(true)
                        delay(800)
                        setFlash(false)
                        delay(800)
                    }
                }
            }
            mode.contains("breath", ignoreCase = true) -> {
                // 呼吸パターン（ON/OFF繰り返し、ゆっくり）
                flashBlinkJob = scope.launch {
                    while (isActive) {
                        setFlash(true)
                        delay(1500)
                        setFlash(false)
                        delay(1500)
                    }
                }
            }
            else -> {
                // 点灯（steady）
                setFlash(true)
            }
        }
    }
    
    /**
     * フラッシュライト状態設定
     */
    private fun setFlash(on: Boolean) {
        val cameraId = flashCameraId ?: return
        try {
            cameraManager.setTorchMode(cameraId, on)
            isFlashOn = on
        } catch (e: Exception) {
            Log.e(TAG, "フラッシュ設定エラー: ${e.message}")
        }
    }

    /**
     * フラッシュライト停止
     */
    fun stopFlash() {
        Log.d(TAG, "フラッシュ停止")
        flashBlinkJob?.cancel()
        flashBlinkJob = null
        setFlash(false)
    }

    /**
     * ワンショットバイブレーション（水噴射など）
     */
    fun shotVibration(durationMs: Long = 300) {
        Log.d(TAG, "ショットバイブレーション: ${durationMs}ms")
        vibrateOnce(durationMs, 255)
    }

    /**
     * ワンショットフラッシュ
     */
    fun shotFlash(durationMs: Long = 200) {
        val cameraId = flashCameraId ?: return
        Log.d(TAG, "ショットフラッシュ: ${durationMs}ms")
        
        scope.launch {
            setFlash(true)
            delay(durationMs)
            setFlash(false)
        }
    }

    /**
     * 全エフェクト停止
     */
    fun stopAll() {
        stopVibration()
        stopFlash()
    }
}
