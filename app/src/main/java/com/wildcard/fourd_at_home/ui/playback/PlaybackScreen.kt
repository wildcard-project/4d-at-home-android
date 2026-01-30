package com.wildcard.fourd_at_home.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.wildcard.fourd_at_home.domain.Content
import com.wildcard.fourd_at_home.domain.EffectType
import com.wildcard.fourd_at_home.ui.theme.NeonRed
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import com.wildcard.fourd_at_home.ui.theme.StatusDisconnected

@Composable
fun PlaybackScreen(
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.showContentSelector) {
            // コンテンツ選択画面
            ContentSelectorScreen(
                contents = uiState.availableContents,
                onContentSelected = { content ->
                    viewModel.loadContent(content)
                },
                isEffectStationConnected = uiState.isEffectStationConnected,
                isMotor1Connected = uiState.isMotor1Connected,
                isMotor2Connected = uiState.isMotor2Connected
            )
        } else {
            // 再生画面
            PlaybackContentScreen(
                viewModel = viewModel,
                uiState = uiState,
                onBack = { viewModel.showContentSelector() }
            )
        }

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

/**
 * コンテンツ選択画面
 */
@Composable
private fun ContentSelectorScreen(
    contents: List<Content>,
    onContentSelected: (Content) -> Unit,
    isEffectStationConnected: Boolean,
    isMotor1Connected: Boolean,
    isMotor2Connected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "4DX@HOME",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "コンテンツを選択してください",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 接続状態
            ConnectionStatusBadge(
                effectStationConnected = isEffectStationConnected,
                motor1Connected = isMotor1Connected,
                motor2Connected = isMotor2Connected
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // コンテンツリスト
        if (contents.isEmpty()) {
            // コンテンツがない場合
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "内蔵コンテンツがありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(contents) { content ->
                    ContentCard(
                        content = content,
                        onClick = { onContentSelected(content) }
                    )
                }
            }
        }
    }
}

/**
 * 接続状態バッジ
 */
@Composable
private fun ConnectionStatusBadge(
    effectStationConnected: Boolean,
    motor1Connected: Boolean,
    motor2Connected: Boolean
) {
    val motorsConnected = listOf(motor1Connected, motor2Connected).count { it }
    val allConnected = effectStationConnected && motorsConnected == 2
    val color = when {
        allConnected -> StatusConnected
        effectStationConnected || motorsConnected > 0 -> Color(0xFFFFA500) // Orange
        else -> Color.Gray
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    allConnected -> "全デバイス接続済"
                    effectStationConnected -> "ES接続中 (Motor: $motorsConnected/2)"
                    motorsConnected > 0 -> "Motor: $motorsConnected/2 (ES未接続)"
                    else -> "未接続"
                },
                color = color,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * コンテンツカード
 */
@Composable
private fun ContentCard(
    content: Content,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // サムネイル代わりのアイコン
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = NeonRed
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 情報
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = content.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // エフェクトタグ
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    content.effectTypes.forEach { type ->
                        EffectTag(type)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 再生ボタン
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonRed
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "再生",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * エフェクトタグ
 */
@Composable
private fun EffectTag(type: EffectType) {
    val (color, icon) = when (type) {
        EffectType.FAN -> StatusConnected to Icons.Default.Air
        EffectType.SPLASH -> Color(0xFF00BCD4) to Icons.Default.WaterDrop
        EffectType.MIST -> Color(0xFF00BCD4) to Icons.Default.WaterDrop
        EffectType.LED -> Color(0xFFFFA500) to Icons.Default.Lightbulb
        EffectType.VIBRATION -> Color(0xFF9C27B0) to Icons.Default.Vibration
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.name,
            modifier = Modifier
                .padding(4.dp)
                .size(16.dp),
            tint = color
        )
    }
}

/**
 * 再生画面
 */
@Composable
private fun PlaybackContentScreen(
    viewModel: PlaybackViewModel,
    uiState: PlaybackUiState,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "戻る"
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = uiState.videoTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 接続状態インジケータ
            ConnectionIndicators(
                isEffectStationConnected = uiState.isEffectStationConnected,
                isMotor1Connected = uiState.isMotor1Connected,
                isMotor2Connected = uiState.isMotor2Connected
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ビデオプレーヤー
        VideoPlayerSection(
            viewModel = viewModel,
            isVideoLoaded = uiState.isVideoLoaded
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 再生コントロール
        PlaybackControlsSection(
            isPlaying = uiState.isPlaying,
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onRewind = { viewModel.rewind() },
            onFastForward = { viewModel.fastForward() },
            onStop = { viewModel.stop() },
            onSeek = { viewModel.seekTo(it) },
            enabled = uiState.isVideoLoaded
        )

        Spacer(modifier = Modifier.height(16.dp))

        // タイムライン情報
        TimelineSection(
            timelineState = uiState.timelineState
        )
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
    isVideoLoaded: Boolean
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
