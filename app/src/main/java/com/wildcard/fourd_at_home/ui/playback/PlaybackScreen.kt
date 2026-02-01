package com.wildcard.fourd_at_home.ui.playback

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.wildcard.fourd_at_home.ui.common.AppBackground
import com.wildcard.fourd_at_home.ui.theme.NeonRed
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import com.wildcard.fourd_at_home.ui.theme.StatusDisconnected

@Composable
fun PlaybackScreen(
    videoId: String,
    onNavigateToVideoSelect: () -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 画面を横向きに固定
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // videoIdに基づいてコンテンツを自動ロード
    if (uiState.selectedContent == null && videoId.isNotEmpty()) {
        viewModel.loadContentById(videoId)
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // 再生画面
            PlaybackContentScreen(
                viewModel = viewModel,
                uiState = uiState,
                onNavigateToVideoSelect = onNavigateToVideoSelect
            )

            // エラー表示
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

/**
 * 再生画面
 */
@Composable
private fun PlaybackContentScreen(
    viewModel: PlaybackViewModel,
    uiState: PlaybackUiState,
    onNavigateToVideoSelect: () -> Unit
) {
    // 新しいスクショ風レイアウト
    val alwaysShowEffects = remember { mutableStateOf(false) } // ★ 常にfalse（トグル削除）
    val showEffectDetail = remember { mutableStateOf(false) }

    // ★ コントロールUIの自動表示/非表示（Reactスタイル）
    var controlsVisible by remember { mutableStateOf(true) }
    val hideControlsTimerRef = remember { mutableStateOf<Job?>(null) }

    // ★ エフェクト表示/非表示トグル
    var effectsVisible by remember { mutableStateOf(true) }

    // 自動非表示ロジック（エフェクトが変化したら短時間表示）
    LaunchedEffect(uiState.timelineState.activeEffects, alwaysShowEffects.value) {
        if (alwaysShowEffects.value) {
            showEffectDetail.value = true
        } else {
            if (uiState.timelineState.activeEffects.isNotEmpty()) {
                showEffectDetail.value = true
                delay(2500)
                showEffectDetail.value = false
            }
        }
    }

    // ★ コントロール自動非表示タイマー（Reactスタイル: 2.5秒後に非表示）
    val startHideControlsTimer = {
        hideControlsTimerRef.value?.cancel()
        hideControlsTimerRef.value = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            delay(2500)
            controlsVisible = false
        }
    }

    // 画面タップ/操作でコントロール表示
    val showControls = {
        controlsVisible = true
        startHideControlsTimer()
    }

    // === 横向き時システムバー非表示 ===
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        
        // 横向き固定
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // システムバーを隠す（ステータスバー + ナビゲーションバー）
        insetsController?.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // === アイドルグレースケール ===
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdle by remember { mutableStateOf(false) }

    // ★ Caption表示状態（常時表示）
    var captionText by remember { mutableStateOf("") }

    // ★ Caption更新（自動非表示なし、常に表示）
    LaunchedEffect(uiState.currentCaption.text, uiState.currentCaption.timestamp) {
        if (uiState.currentCaption.text.isNotEmpty()) {
            captionText = uiState.currentCaption.text
        }
    }

    // 操作検知でタイマーリセット
    val resetIdleTimer = {
        lastInteractionTime = System.currentTimeMillis()
        isIdle = false
    }

    // アイドル監視ループ
    LaunchedEffect(lastInteractionTime) {
        while (true) {
            delay(500) // 500msごとにチェック
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            isIdle = elapsed > 4000 // 4秒でアイドル
        }
    }

    // グレースケールアニメーション（彩度：1.0 → 0.0）
    val saturation by animateFloatAsState(
        targetValue = if (isIdle) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "idle_grayscale"
    )

    // ColorMatrix（彩度調整）
    val colorMatrix = ColorMatrix().apply {
        setToSaturation(saturation)
    }

    // メイン：動画エリア（全画面化） + 左エフェクト列 + 中央薄UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing) // SafeArea対応
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        resetIdleTimer()
                        showControls() // ★ タップでコントロール表示
                    },
                    onPress = { 
                        resetIdleTimer()
                        showControls() // ★ プレスでコントロール表示
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
            // 動画本体（既存の VideoPlayerSection を使う）
            VideoPlayerSection(
                viewModel = viewModel,
                isVideoLoaded = uiState.isVideoLoaded,
                activeEffects = uiState.timelineState.activeEffects
            )

            // ★ Caption表示（画面上部中央、effectsVisibleに連動）
            androidx.compose.animation.AnimatedVisibility(
                visible = effectsVisible && captionText.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .widthIn(max = 500.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.75f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = captionText,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // 左端のエフェクト縦並び（アイコン表示）※グレースケール対象外
            // ★ effectsVisible で表示/非表示制御
            androidx.compose.animation.AnimatedVisibility(
                visible = effectsVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // VIBRATION - 衝
                    val vibActive = uiState.timelineState.activeEffects.any { it.equals("VIBRATION", ignoreCase = true) }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (vibActive) Color(0xFF9C27B0).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.1f)
                            )
                            .border(2.dp, if (vibActive) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "衝撃",
                            modifier = Modifier.size(24.dp),
                            tint = if (vibActive) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // LED/COLOR/FLASH - 光
                    val ledActive = uiState.timelineState.activeEffects.any { 
                        it.equals("LED", ignoreCase = true) || 
                        it.equals("COLOR", ignoreCase = true) || 
                        it.equals("FLASH", ignoreCase = true) 
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (ledActive) Color(0xFFFFA500).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.1f)
                            )
                            .border(2.dp, if (ledActive) Color(0xFFFFA500) else Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "光",
                            modifier = Modifier.size(24.dp),
                            tint = if (ledActive) Color(0xFFFFA500) else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // WIND - 風
                    val windActive = uiState.timelineState.activeEffects.any { 
                        it.equals("WIND", ignoreCase = true) || it.equals("FAN", ignoreCase = true) 
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (windActive) Color(0xFF4CAF50).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.1f)
                            )
                            .border(2.dp, if (windActive) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Air,
                            contentDescription = "風",
                            modifier = Modifier.size(24.dp),
                            tint = if (windActive) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // WATER/MIST - 水
                    val waterActive = uiState.timelineState.activeEffects.any { 
                        it.equals("WATER", ignoreCase = true) || 
                        it.equals("MIST", ignoreCase = true) || 
                        it.equals("SPLASH", ignoreCase = true)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (waterActive) Color(0xFF00BCD4).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.1f)
                            )
                            .border(2.dp, if (waterActive) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = "水",
                            modifier = Modifier.size(24.dp),
                            tint = if (waterActive) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 中央の薄い操作UI（prev / play / next）※グレースケール対象
            // ★ controlsVisible で表示/非表示制御（Reactスタイル）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .drawWithCache {
                            val paint = Paint().apply {
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(colorMatrix)
                            }
                            onDrawWithContent {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                    drawContent()
                                    canvas.restore()
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures { resetIdleTimer(); showControls() } },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // prev
                    IconButton(onClick = {
                        resetIdleTimer()
                        showControls()
                        viewModel.rewind()
                    }) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(text = "◀", color = Color.White.copy(alpha = 0.8f))
                        }
                    }

                    // play/pause
                    IconButton(onClick = {
                        resetIdleTimer()
                        showControls()
                        viewModel.togglePlayPause()
                    }) {
                        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            if (uiState.isPlaying) {
                                // 再生中 = 塗りつぶし四角（停止ボタン）
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.White.copy(alpha = 0.9f), androidx.compose.ui.graphics.RectangleShape)
                                )
                            } else {
                                // 停止中 = 枠線四角 + 三角（再生ボタン）
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .border(2.dp, Color.White.copy(alpha = 0.8f), androidx.compose.ui.graphics.RectangleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "▶", color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                        }
                    }

                    // next
                    IconButton(onClick = {
                        resetIdleTimer()
                        showControls()
                        viewModel.fastForward()
                    }) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(text = "▶", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // ★ 右下のエフェクト表示/非表示トグルボタン（Reactスタイル）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 60.dp)
            ) {
                IconButton(
                    onClick = {
                        resetIdleTimer()
                        showControls()
                        effectsVisible = !effectsVisible
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp))
                        .drawWithCache {
                            val paint = Paint().apply {
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(colorMatrix)
                            }
                            onDrawWithContent {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                    drawContent()
                                    canvas.restore()
                                }
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (effectsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (effectsVisible) "エフェクト非表示" else "エフェクト表示",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // ★ シークバー（下部中央、自動非表示、背景なし）
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible && uiState.duration > 0,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .drawWithCache {
                            val paint = Paint().apply {
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(colorMatrix)
                            }
                            onDrawWithContent {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                    drawContent()
                                    canvas.restore()
                                }
                            }
                        }
                ) {
                    Slider(
                        value = if (uiState.duration > 0) (uiState.currentPosition.toFloat() / uiState.duration) else 0f,
                        onValueChange = { progress ->
                            resetIdleTimer()
                            showControls()
                            val newPosition = (progress * uiState.duration).toLong()
                            viewModel.seekTo(newPosition)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatTime(uiState.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ★ 動画終了時ボタン（中央）
            val videoEnded = uiState.currentPosition >= uiState.duration && uiState.duration > 0 && uiState.currentPosition > 0
            androidx.compose.animation.AnimatedVisibility(
                visible = videoEnded,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Button(
                    onClick = {
                        onNavigateToVideoSelect()
                    },
                    modifier = Modifier
                        .padding(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "他の素晴らしい作品に出会う",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

@Composable
private fun ConnectionIndicators(
    isEffectStationConnected: Boolean,
    isMotor1Connected: Boolean,
    isMotor2Connected: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectionDot(label = "ES", isConnected = isEffectStationConnected)
        ConnectionDot(label = "M1", isConnected = isMotor1Connected)
        ConnectionDot(label = "M2", isConnected = isMotor2Connected)
    }
}

@Composable
private fun ConnectionDot(
    label: String,
    isConnected: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isConnected) StatusConnected else StatusDisconnected)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun VideoPlayerSection(
    viewModel: PlaybackViewModel,
    isVideoLoaded: Boolean,
    activeEffects: List<String> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            if (isVideoLoaded) {
                // ExoPlayer表示
                val player = viewModel.getPlayer()
                if (player != null) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // 読み込み中
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "動画を読み込み中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // シークバー
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = NeonRed,
                    activeTrackColor = NeonRed,
                    disabledThumbColor = Color.Gray,
                    disabledActiveTrackColor = Color.Gray
                )
            )

            // 時間表示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                )
                Text(
                    text = if (enabled) formatTime(duration) else "--:--",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // コントロールボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 停止
                IconButton(onClick = onStop, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止",
                        tint = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 10秒戻る
                IconButton(onClick = onRewind, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "10秒戻る",
                        tint = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 再生/一時停止
                IconButton(
                    onClick = onTogglePlayPause,
                    enabled = enabled,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (enabled) NeonRed else Color.Gray)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "一時停止" else "再生",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 10秒進む
                IconButton(onClick = onFastForward, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "10秒進む",
                        tint = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // プレースホルダー（対称性のため）
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun TimelineSection(
    timelineState: com.wildcard.fourd_at_home.playback.PlaybackSyncState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = NeonRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "タイムライン",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (timelineState.isLoaded) {
                // タイムライン情報
                Text(
                    text = timelineState.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 進捗表示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "イベント: ${timelineState.currentEventIndex} / ${timelineState.totalEvents}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "長さ: ${formatTime(timelineState.totalDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 進捗バー
                LinearProgressIndicator(
                    progress = {
                        if (timelineState.totalEvents > 0) {
                            timelineState.currentEventIndex.toFloat() / timelineState.totalEvents.toFloat()
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonRed,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ステータス
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (timelineState.isPlaying) StatusConnected else StatusDisconnected
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (timelineState.isPlaying) "同期再生中" else "停止中",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timelineState.isPlaying) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "タイムラインを読み込み中...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * ミリ秒を MM:SS 形式にフォーマット
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
