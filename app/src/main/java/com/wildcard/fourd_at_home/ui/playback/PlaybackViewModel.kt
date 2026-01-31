package com.wildcard.fourd_at_home.ui.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wildcard.fourd_at_home.ble.BleConnection
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.DeviceType
import com.wildcard.fourd_at_home.domain.Content
import com.wildcard.fourd_at_home.domain.ContentLibrary
import com.wildcard.fourd_at_home.playback.PlaybackSyncEngine
import com.wildcard.fourd_at_home.playback.PlaybackSyncState
import com.wildcard.fourd_at_home.playback.TimelineFile
import com.wildcard.fourd_at_home.playback.TimelineParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 再生画面のUI状態
 */
data class PlaybackUiState(
    // コンテンツ状態
    val availableContents: List<Content> = ContentLibrary.contents,
    val selectedContent: Content? = null,
    
    // ビデオ状態
    val videoUri: Uri? = null,
    val videoTitle: String = "",
    val isVideoLoaded: Boolean = false,
    
    // タイムライン状態
    val timelineState: PlaybackSyncState = PlaybackSyncState(),
    
    // 再生状態
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    
    // 接続状態
    val isEffectStationConnected: Boolean = false,
    val isMotor1Connected: Boolean = false,
    val isMotor2Connected: Boolean = false,
    
    // その他
    val error: String? = null,
    val showContentSelector: Boolean = true
)

/**
 * 再生画面のViewModel
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncEngine: PlaybackSyncEngine,
    private val timelineParser: TimelineParser,
    private val deviceManager: BleDeviceManager
) : ViewModel() {
    companion object {
        private const val TAG = "PlaybackViewModel"
        private const val POSITION_UPDATE_INTERVAL_MS = 16L
    }

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    // ExoPlayer
    private var exoPlayer: ExoPlayer? = null
    private var positionUpdateJob: Job? = null

    // 接続状態
    val connections: StateFlow<Map<String, BleConnection>> = deviceManager.connections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state changed: $playbackState")
            when (playbackState) {
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Player STATE_IDLE")
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Player STATE_BUFFERING")
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "Player STATE_READY")
                    exoPlayer?.let { player ->
                        _uiState.value = _uiState.value.copy(
                            isVideoLoaded = true,
                            duration = player.duration
                        )
                        Log.d(TAG, "Video loaded, duration: ${player.duration}ms")
                    }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Player STATE_ENDED")
                    onPlaybackEnded()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "IsPlaying changed: $isPlaying")
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            
            if (isPlaying) {
                syncEngine.start()
                startPositionUpdates()
            } else {
                Log.d(TAG, "一時停止 - 全エフェクト停止コマンドを送信")
                syncEngine.pause()
                stopPositionUpdates()
            }
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            _uiState.value = _uiState.value.copy(
                error = "再生エラー: ${error.message}"
            )
        }
    }

    init {
        initializePlayer()
        observeConnections()
        observeSyncState()
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
    }

    private fun observeConnections() {
        viewModelScope.launch {
            deviceManager.connections.collect { connections ->
                _uiState.value = _uiState.value.copy(
                    isEffectStationConnected = connections.values.any {
                        it.deviceType == DeviceType.EFFECT_STATION && it.state == ConnectionState.READY
                    },
                    isMotor1Connected = connections.values.any {
                        it.deviceType == DeviceType.ACTION_DRIVE_1 && it.state == ConnectionState.READY
                    },
                    isMotor2Connected = connections.values.any {
                        it.deviceType == DeviceType.ACTION_DRIVE_2 && it.state == ConnectionState.READY
                    }
                )
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncEngine.state.collect { syncState ->
                _uiState.value = _uiState.value.copy(timelineState = syncState)
            }
        }
    }

    /**
     * ExoPlayerインスタンスを取得
     */
    fun getPlayer(): ExoPlayer? = exoPlayer

    /**
     * ビデオを読み込む
     */
    fun loadVideo(uri: Uri, title: String = "") {
        Log.d(TAG, "ビデオ読み込み: $uri")
        
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            
            _uiState.value = _uiState.value.copy(
                videoUri = uri,
                videoTitle = title.ifEmpty { uri.lastPathSegment ?: "動画" }
            )
        }
    }

    /**
     * タイムラインを読み込む
     */
    fun loadTimeline(uri: Uri) {
        Log.d(TAG, "タイムライン読み込み: $uri")
        
        viewModelScope.launch {
            val result = timelineParser.parseFromUri(uri)
            result.fold(
                onSuccess = { timeline ->
                    syncEngine.loadTimeline(timeline)
                    _uiState.value = _uiState.value.copy(error = null)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "タイムライン読み込み失敗: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * サンプルタイムラインを読み込む
     */
    fun loadSampleTimeline() {
        val sampleTimeline = timelineParser.createSampleTimeline()
        syncEngine.loadTimeline(sampleTimeline)
    }

    /**
     * 内蔵コンテンツを読み込む
     */
    fun loadContent(content: Content) {
        Log.d(TAG, "内蔵コンテンツ読み込み: ${content.title}")
        
        viewModelScope.launch {
            try {
                // 動画を読み込み (ExoPlayerのassets用URIスキーム)
                val videoUri = Uri.parse("file:///android_asset/${content.videoAssetPath}")
                Log.d(TAG, "Video URI: $videoUri")
                
                exoPlayer?.let { player ->
                    val mediaItem = MediaItem.fromUri(videoUri)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                }
                
                // UIを即座に更新（動画読み込み開始）
                _uiState.value = _uiState.value.copy(
                    selectedContent = content,
                    videoUri = videoUri,
                    videoTitle = content.title,
                    showContentSelector = false,
                    error = null
                )
                
                // タイムラインを読み込み
                val result = timelineParser.parseFromAssets(content.timelineAssetPath)
                result.fold(
                    onSuccess = { timeline ->
                        syncEngine.loadTimeline(timeline)
                        Log.d(TAG, "タイムライン読み込み完了: ${timeline.events.size}イベント")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "タイムライン読み込み失敗", error)
                        _uiState.value = _uiState.value.copy(
                            error = "タイムライン読み込み失敗: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "コンテンツ読み込み失敗", e)
                _uiState.value = _uiState.value.copy(
                    error = "コンテンツ読み込み失敗: ${e.message}"
                )
            }
        }
    }

    /**
     * コンテンツ選択画面を表示
     */
    fun showContentSelector() {
        _uiState.value = _uiState.value.copy(showContentSelector = true)
    }

    /**
     * コンテンツ選択画面を非表示
     */
    fun hideContentSelector() {
        _uiState.value = _uiState.value.copy(showContentSelector = false)
    }

    /**
     * 再生/一時停止を切り替え
     */
    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    /**
     * 再生開始
     */
    fun play() {
        exoPlayer?.play()
    }

    /**
     * 一時停止
     */
    fun pause() {
        Log.d(TAG, "pause() 呼び出し - 一時停止処理を実行")
        exoPlayer?.pause()
        // ExoPlayerのpause()により、onIsPlayingChanged(false)が呼ばれ、
        // そこでsyncEngine.pause()と全エフェクト停止が実行される
    }

    /**
     * 停止
     */
    fun stop() {
        Log.d(TAG, "stop() 呼び出し - 停止処理を実行")
        exoPlayer?.let { player ->
            player.pause()
            player.seekTo(0)
        }
        // syncEngine.stop()内でsendAllDevicesOff()が呼ばれる
        syncEngine.stop()
    }

    /**
     * シーク
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        syncEngine.onSeek(positionMs)
    }

    /**
     * 10秒戻る
     */
    fun rewind() {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
            seekTo(newPosition)
        }
    }

    /**
     * 10秒進む
     */
    fun fastForward() {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
            seekTo(newPosition)
        }
    }

    /**
     * 再生終了時の処理
     */
    private fun onPlaybackEnded() {
        Log.d(TAG, "再生終了")
        syncEngine.stop()
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    /**
     * ポジション更新を開始
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val position = player.currentPosition
                    val buffered = player.bufferedPosition
                    
                    _uiState.value = _uiState.value.copy(
                        currentPosition = position,
                        bufferedPosition = buffered
                    )
                    
                    // 同期エンジンに位置を通知
                    syncEngine.updatePosition(position)
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * ポジション更新を停止
     */
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * エラーをクリア
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        syncEngine.stop()
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.release()
        }
        exoPlayer = null
    }
}
