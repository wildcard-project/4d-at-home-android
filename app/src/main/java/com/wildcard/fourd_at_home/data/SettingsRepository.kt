package com.wildcard.fourd_at_home.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fourd_at_home_settings")

/**
 * アプリ設定の永続化を管理するリポジトリ
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // === 接続設定 ===
        private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val KEY_RECONNECT_ATTEMPTS = intPreferencesKey("reconnect_attempts")
        private val KEY_SAVED_DEVICE_ADDRESSES = stringSetPreferencesKey("saved_device_addresses")
        
        // === 再生設定 ===
        private val KEY_SYNC_OFFSET_MS = longPreferencesKey("sync_offset_ms")
        private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
        private val KEY_LAST_VIDEO_URI = stringPreferencesKey("last_video_uri")
        private val KEY_LAST_TIMELINE_URI = stringPreferencesKey("last_timeline_uri")
        
        // === エフェクト設定 ===
        private val KEY_MASTER_INTENSITY = intPreferencesKey("master_intensity")
        private val KEY_FAN_ENABLED = booleanPreferencesKey("fan_enabled")
        private val KEY_WATER_ENABLED = booleanPreferencesKey("water_enabled")
        private val KEY_MIST_ENABLED = booleanPreferencesKey("mist_enabled")
        private val KEY_LED_ENABLED = booleanPreferencesKey("led_enabled")
        private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        
        // === 安全設定 ===
        private val KEY_SAFETY_TIMEOUT_ENABLED = booleanPreferencesKey("safety_timeout_enabled")
        private val KEY_SAFETY_TIMEOUT_SECONDS = intPreferencesKey("safety_timeout_seconds")
        
        // デフォルト値
        const val DEFAULT_RECONNECT_ATTEMPTS = 3
        const val DEFAULT_SYNC_OFFSET_MS = 0L
        const val DEFAULT_MASTER_INTENSITY = 100  // パーセント
        const val DEFAULT_SAFETY_TIMEOUT_SECONDS = 30
    }

    // === 接続設定 ===
    
    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_RECONNECT] ?: true
    }
    
    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_RECONNECT] = enabled
        }
    }
    
    val reconnectAttempts: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_RECONNECT_ATTEMPTS] ?: DEFAULT_RECONNECT_ATTEMPTS
    }
    
    suspend fun setReconnectAttempts(attempts: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RECONNECT_ATTEMPTS] = attempts.coerceIn(1, 10)
        }
    }
    
    val savedDeviceAddresses: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAVED_DEVICE_ADDRESSES] ?: emptySet()
    }
    
    suspend fun addSavedDevice(address: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_SAVED_DEVICE_ADDRESSES] ?: emptySet()
            preferences[KEY_SAVED_DEVICE_ADDRESSES] = current + address
        }
    }
    
    suspend fun removeSavedDevice(address: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_SAVED_DEVICE_ADDRESSES] ?: emptySet()
            preferences[KEY_SAVED_DEVICE_ADDRESSES] = current - address
        }
    }
    
    suspend fun clearSavedDevices() {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAVED_DEVICE_ADDRESSES] = emptySet()
        }
    }
    
    // === 再生設定 ===
    
    val syncOffsetMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_SYNC_OFFSET_MS] ?: DEFAULT_SYNC_OFFSET_MS
    }
    
    suspend fun setSyncOffsetMs(offsetMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYNC_OFFSET_MS] = offsetMs.coerceIn(-1000, 1000)
        }
    }
    
    val autoPlay: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_PLAY] ?: false
    }
    
    suspend fun setAutoPlay(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_PLAY] = enabled
        }
    }
    
    val lastVideoUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_VIDEO_URI]
    }
    
    suspend fun setLastVideoUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_LAST_VIDEO_URI] = uri
            } else {
                preferences.remove(KEY_LAST_VIDEO_URI)
            }
        }
    }
    
    val lastTimelineUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_TIMELINE_URI]
    }
    
    suspend fun setLastTimelineUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_LAST_TIMELINE_URI] = uri
            } else {
                preferences.remove(KEY_LAST_TIMELINE_URI)
            }
        }
    }
    
    // === エフェクト設定 ===
    
    val masterIntensity: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MASTER_INTENSITY] ?: DEFAULT_MASTER_INTENSITY
    }
    
    suspend fun setMasterIntensity(intensity: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MASTER_INTENSITY] = intensity.coerceIn(0, 100)
        }
    }
    
    val fanEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FAN_ENABLED] ?: true
    }
    
    suspend fun setFanEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FAN_ENABLED] = enabled
        }
    }
    
    val waterEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WATER_ENABLED] ?: true
    }
    
    suspend fun setWaterEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WATER_ENABLED] = enabled
        }
    }
    
    val mistEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MIST_ENABLED] ?: true
    }
    
    suspend fun setMistEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MIST_ENABLED] = enabled
        }
    }
    
    val ledEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LED_ENABLED] ?: true
    }
    
    suspend fun setLedEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LED_ENABLED] = enabled
        }
    }
    
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIBRATION_ENABLED] ?: true
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VIBRATION_ENABLED] = enabled
        }
    }
    
    // === 安全設定 ===
    
    val safetyTimeoutEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAFETY_TIMEOUT_ENABLED] ?: true
    }
    
    suspend fun setSafetyTimeoutEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAFETY_TIMEOUT_ENABLED] = enabled
        }
    }
    
    val safetyTimeoutSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAFETY_TIMEOUT_SECONDS] ?: DEFAULT_SAFETY_TIMEOUT_SECONDS
    }
    
    suspend fun setSafetyTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAFETY_TIMEOUT_SECONDS] = seconds.coerceIn(10, 300)
        }
    }
    
    // === ユーティリティ ===
    
    /**
     * 全設定をリセット
     */
    suspend fun resetAllSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
