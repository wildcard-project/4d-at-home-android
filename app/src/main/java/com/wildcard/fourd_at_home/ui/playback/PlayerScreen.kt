package com.wildcard.fourd_at_home.ui.playback

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wildcard.fourd_at_home.playback.EffectType
import com.wildcard.fourd_at_home.ui.navigation.AppChrome
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import kotlinx.coroutines.delay

private val PlayerRed = Color(0xFFE50914)
private val PanelBg = Color(0xF2141414)
private val SubText = Color(0xFFB3B3B3)

/** 設定パネルで切り替えるエフェクトのまとまり */
private data class EffectGroup(val label: String, val desc: String, val icon: ImageVector, val types: List<EffectType>)

private val effectGroups = listOf(
    EffectGroup("振動", "ActionDrive（背中・お尻）", Icons.Default.Vibration, listOf(EffectType.VIBRATION)),
    EffectGroup("風", "EffectStation のファン", Icons.Default.Air, listOf(EffectType.WIND)),
    EffectGroup("光", "LED のフラッシュと色", Icons.Default.Lightbulb, listOf(EffectType.FLASH, EffectType.COLOR)),
    EffectGroup("水", "水しぶきとミスト", Icons.Default.WaterDrop, listOf(EffectType.WATER, EffectType.MIST)),
)

/**
 * Netflix 風の全画面プレーヤー。画面をタップすると操作バーが出て、しばらくすると消える
 */
@Composable
fun FullscreenPlayer(
    viewModel: PlaybackViewModel,
    uiState: PlaybackUiState,
    onBack: () -> Unit
) {
    // 再生中は左のナビゲーションとスマホの上下のバーを隠す
    val view = LocalView.current
    DisposableEffect(Unit) {
        AppChrome.fullscreen.value = true
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            AppChrome.fullscreen.value = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler { onBack() }

    var controlsVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableStateOf(0L) }

    // 再生中は 3.5 秒触らなければ操作バーを消す（設定パネルを開いている間は消さない）
    LaunchedEffect(controlsVisible, uiState.isPlaying, settingsOpen, lastTouch) {
        if (controlsVisible && uiState.isPlaying && !settingsOpen) {
            delay(3500)
            controlsVisible = false
        }
    }
    val touched = { lastTouch = System.currentTimeMillis() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (settingsOpen) settingsOpen = false else controlsVisible = !controlsVisible
                touched()
            }
    ) {
        // 動画
        val player = viewModel.getPlayer()
        if (uiState.isVideoLoaded && player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PlayerRed)
                Spacer(Modifier.height(12.dp))
                Text("読み込み中…", color = SubText)
            }
        }

        // 操作バー
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
            Box(Modifier.fillMaxSize()) {
                // 上と下を暗くして文字を読みやすく
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                )

                TopControls(
                    title = uiState.selectedContent?.title ?: uiState.videoTitle,
                    es = uiState.isEffectStationConnected,
                    ad = uiState.isActionDriveConnected,
                    onBack = onBack,
                    onSettings = { settingsOpen = true; touched() }
                )

                CenterControls(
                    isPlaying = uiState.isPlaying,
                    enabled = uiState.isVideoLoaded,
                    onRewind = { viewModel.rewind(); touched() },
                    onToggle = { viewModel.togglePlayPause(); touched() },
                    onForward = { viewModel.fastForward(); touched() },
                    modifier = Modifier.align(Alignment.Center)
                )

                BottomControls(
                    uiState = uiState,
                    onSeek = { viewModel.seekTo(it); touched() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // 右から出る設定パネル
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(tween(250)) { it },
            exit = slideOutHorizontally(tween(250)) { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsPanel(
                uiState = uiState,
                onToggle = { group, enabled -> group.types.forEach { viewModel.setEffectEnabled(it, enabled) } },
                onClose = { settingsOpen = false }
            )
        }
    }
}

@Composable
private fun TopControls(title: String, es: Boolean, ad: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "戻る", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(PlayerRed)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) { Text("4D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        StatusPill("EffectStation", es)
        Spacer(Modifier.width(8.dp))
        StatusPill("ActionDrive", ad)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "再生の設定", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun StatusPill(label: String, connected: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (connected) StatusConnected else Color(0xFF777777))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (connected) Color.White else SubText, fontSize = 11.sp)
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    enabled: Boolean,
    onRewind: () -> Unit,
    onToggle: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        IconButton(onClick = onRewind, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = "10秒戻る", tint = Color.White, modifier = Modifier.size(44.dp))
        }
        IconButton(onClick = onToggle, enabled = enabled, modifier = Modifier.size(84.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        }
        IconButton(onClick = onForward, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Default.Forward10, contentDescription = "10秒進む", tint = Color.White, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun BottomControls(uiState: PlaybackUiState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    // つまみを動かしている間は手元の値を表示し、離したときにシークする
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = uiState.duration.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else uiState.currentPosition.toFloat()

    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = shown.coerceIn(0f, duration.toFloat()),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
                valueRange = 0f..duration.toFloat(),
                enabled = uiState.isVideoLoaded,
                colors = SliderDefaults.colors(
                    thumbColor = PlayerRed,
                    activeTrackColor = PlayerRed,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "-" + formatClock(duration - shown.toLong()),
                color = Color.White,
                fontSize = 14.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyncBadge(uiState)
            Spacer(Modifier.weight(1f))
            Text(
                "${formatClock(shown.toLong())} / ${formatClock(duration)}",
                color = SubText,
                fontSize = 12.sp
            )
        }
    }
}

/** 4D の同期状態（同期中は赤い点が明滅） */
@Composable
private fun SyncBadge(uiState: PlaybackUiState) {
    val t = uiState.timelineState
    val pulse by rememberInfiniteTransition(label = "sync").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "syncPulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .graphicsLayer { alpha = if (t.isPlaying) pulse else 1f }
                .clip(CircleShape)
                .background(if (t.isPlaying) PlayerRed else Color(0xFF777777))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                !t.isLoaded -> "4D タイムライン読み込み中"
                t.isPlaying -> "4D 同期中"
                else -> "4D 待機中"
            },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (t.isLoaded) {
            Text("   エフェクト ${t.currentEventIndex} / ${t.totalEvents}", color = SubText, fontSize = 12.sp)
        }
        if (uiState.disabledEffects.isNotEmpty()) {
            Text("   一部のエフェクトを切っています", color = Color(0xFFFFB74D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsPanel(
    uiState: PlaybackUiState,
    onToggle: (EffectGroup, Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(PanelBg)
            // パネルの中のタップで閉じないようにする
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("再生の設定", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Text("4D エフェクト", color = SubText, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        effectGroups.forEach { g ->
            val enabled = g.types.none { it in uiState.disabledEffects }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) { Icon(g.icon, contentDescription = null, tint = if (enabled) PlayerRed else SubText) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.label, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(g.desc, color = SubText, fontSize = 12.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle(g, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = PlayerRed, checkedThumbColor = Color.White)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("デバイス", color = SubText, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        DeviceLine("EffectStation", "風・光・水", uiState.isEffectStationConnected)
        DeviceLine("ActionDrive", "振動（背中・お尻）", uiState.isActionDriveConnected)
        Spacer(Modifier.height(18.dp))
        Text("タイムライン", color = SubText, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        val t = uiState.timelineState
        Text(
            if (t.isLoaded) "${t.totalEvents} 個のエフェクト・長さ ${formatClock(t.totalDuration)}" else "読み込み中",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DeviceLine(name: String, role: String, connected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) StatusConnected else Color(0xFF777777)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 14.sp)
            Text(role, color = SubText, fontSize = 12.sp)
        }
        Text(if (connected) "接続中" else "未接続", color = if (connected) StatusConnected else SubText, fontSize = 12.sp)
    }
}

private fun formatClock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}
