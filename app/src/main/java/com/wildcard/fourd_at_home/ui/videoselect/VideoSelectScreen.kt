package com.wildcard.fourd_at_home.ui.videoselect

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildcard.fourd_at_home.domain.Content
import com.wildcard.fourd_at_home.domain.ContentLibrary
import com.wildcard.fourd_at_home.domain.EffectType
import com.wildcard.fourd_at_home.ui.common.AppBackground
import com.wildcard.fourd_at_home.ui.theme.NeonRed
import kotlin.math.absoluteValue

/**
 * 動画選択画面
 * カルーセル風カード表示で動画を選択
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoSelectScreen(
    onVideoSelected: (Content) -> Unit
) {
    val contents = ContentLibrary.contents

    AppBackground {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (contents.isEmpty()) {
                // コンテンツがない場合
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "内蔵コンテンツがありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { contents.size }
                )
                
                val currentContent = contents.getOrNull(pagerState.currentPage)

                // 背景サムネイル（中央の作品）
                currentContent?.let { content ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1A1A2E),
                                        Color(0xFF0F0F1E)
                                    )
                                )
                            )
                    ) {
                        // 下部グラデーションオーバーレイ
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Black.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                // ロゴ
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "4D@HOME",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeonRed,
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // カルーセル
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 60.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val content = contents[page]
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    
                    // Y軸回転（-1.0〜1.0 の範囲を -20度〜20度にマッピング）
                    val rotationY = (pageOffset * 20f).coerceIn(-20f, 20f)
                    
                    // Z軸回転（水平傾き）（-1.0〜1.0 の範囲を -5度〜5度にマッピング）
                    val rotationZ = (pageOffset * 5f).coerceIn(-5f, 5f)
                    
                    // スケール（中央は1.0、左右は0.88）
                    val scale = 1f - (pageOffset.absoluteValue * 0.12f).coerceIn(0f, 0.12f)
                    
                    // 透明度（中央は1.0、左右は0.7）
                    val alpha = 1f - (pageOffset.absoluteValue * 0.3f).coerceIn(0f, 0.3f)
                    
                    val isCenterPage = page == pagerState.currentPage

                    if (isCenterPage) {
                        // 中央カード：上スワイプジェスチャー + Hero遷移
                        var dragOffset by remember { mutableStateOf(0f) }
                        Box(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (dragOffset < -100f) {
                                                // 上スワイプでSettings画面へ
                                                onVideoSelected(content)
                                            }
                                            dragOffset = 0f
                                        },
                                        onVerticalDrag = { _, dragAmount ->
                                            dragOffset += dragAmount
                                        }
                                    )
                                }
                        ) {
                            CarouselCard(
                                content = content,
                                rotationY = rotationY,
                                rotationZ = rotationZ,
                                scale = scale,
                                alpha = alpha,
                                isCenterPage = isCenterPage,
                                onClick = { onVideoSelected(content) }
                            )
                        }
                    } else {
                        // 左右のカード：通常表示
                        CarouselCard(
                            content = content,
                            rotationY = rotationY,
                            rotationZ = rotationZ,
                            scale = scale,
                            alpha = alpha,
                            isCenterPage = isCenterPage,
                            onClick = { onVideoSelected(content) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * 動画ID → サムネイルパスのマッピング
 * 未設定の場合はプレースホルダー表示
 */
val thumbnailMap = mapOf(
    "wild_speed_fire_boost" to "sample_thumbnail.png"
    // 他の動画IDとサムネを追加
)

@Composable
fun AssetImage(
    assetPath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        if (assetPath.isNullOrBlank()) return@remember null
        try {
            context.assets.open(assetPath).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                bmp?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        // フォールバック表示（既存のプレースホルダー風）
        Box(
            modifier = modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF0F0F1E)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}

/**
 * カルーセルカード（縦長カード）
 */
@Composable
fun CarouselCard(
    content: Content,
    rotationY: Float,
    rotationZ: Float,
    scale: Float,
    alpha: Float,
    isCenterPage: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        label = "cardScale"
    )
    
    val animatedRotationY by animateFloatAsState(
        targetValue = rotationY,
        label = "cardRotationY"
    )
    
    val animatedRotationZ by animateFloatAsState(
        targetValue = rotationZ,
        label = "cardRotationZ"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        label = "cardAlpha"
    )

    val density = LocalDensity.current.density

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                this.alpha = animatedAlpha
                // Y軸回転（左右に回転）
                this.rotationY = animatedRotationY
                // Z軸回転（水平に傾く）
                this.rotationZ = animatedRotationZ
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isCenterPage) 16.dp else 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
            ) {
                // サムネイル（assets 内の画像を表示）
                val context = LocalContext.current
                val assetPath = remember(content.id) {
                    val candidates = mutableListOf<String>()
                    thumbnailMap[content.id]?.let { candidates.add(it) }
                    candidates.addAll(listOf("${content.id}.jpeg", "${content.id}.jpg", "${content.id}.png"))

                    candidates.firstOrNull { name ->
                        try {
                            context.assets.open(name).close()
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                }

                AssetImage(
                    assetPath = assetPath,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Crop
                )

                // 下部グラデーションscrim
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                // 左下タイトルオーバーレイ
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = content.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // エフェクトタグ
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            content.effectTypes.take(3).forEach { type ->
                                EffectTag(type)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * エフェクトタグ
 */
@Composable
private fun EffectTag(type: EffectType) {
    val (color, icon, text) = when (type) {
        EffectType.FAN -> Triple(Color(0xFF4CAF50), Icons.Default.Air, "風")
        EffectType.SPLASH -> Triple(Color(0xFF00BCD4), Icons.Default.WaterDrop, "水")
        EffectType.MIST -> Triple(Color(0xFF00BCD4), Icons.Default.WaterDrop, "霧")
        EffectType.LED -> Triple(Color(0xFFFFA500), Icons.Default.Lightbulb, "光")
        EffectType.VIBRATION -> Triple(Color(0xFF9C27B0), Icons.Default.Vibration, "衝")
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
