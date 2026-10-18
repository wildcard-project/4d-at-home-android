package com.wildcard.fourd_at_home.ui.playback

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildcard.fourd_at_home.domain.Content
import com.wildcard.fourd_at_home.domain.EffectType
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Netflix 風ホーム画面の色
private val HomeBlack = Color(0xFF0B0B0B)
private val HomeRed = Color(0xFFE50914)
private val HomeGrey = Color(0xFFB3B3B3)

/** まだ遊べない作品（ホームの「近日公開」に並べる） */
private data class ComingSoon(
    val title: String,
    val genre: String,
    val colors: List<Color>,
    val icon: ImageVector
)

private val comingSoonList = listOf(
    ComingSoon("HIGHWAY STORM", "カーアクション", listOf(Color(0xFF3A0A0A), Color(0xFFB71C1C)), Icons.Default.Air),
    ComingSoon("DEEP OCEAN", "海洋アドベンチャー", listOf(Color(0xFF02142B), Color(0xFF0277BD)), Icons.Default.WaterDrop),
    ComingSoon("THUNDER STRIKE", "ディザスター", listOf(Color(0xFF1A1033), Color(0xFF5E35B1)), Icons.Default.Lightbulb),
    ComingSoon("QUAKE ZONE", "パニック", listOf(Color(0xFF2B1A05), Color(0xFFE65100)), Icons.Default.Vibration),
    ComingSoon("SKY RIDERS", "スカイアクション", listOf(Color(0xFF06231C), Color(0xFF00897B)), Icons.Default.Air),
    ComingSoon("NIGHT CHASE", "サスペンス", listOf(Color(0xFF101010), Color(0xFF455A64)), Icons.Default.Lightbulb),
)

/** assets の画像を読み込む（無ければ null） */
@Composable
private fun rememberAssetImage(path: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return image
}

/**
 * Netflix 風のホーム画面（作品の選択）
 */
@Composable
fun NetflixHomeScreen(
    contents: List<Content>,
    onContentSelected: (Content) -> Unit,
    isEffectStationConnected: Boolean,
    isActionDriveConnected: Boolean
) {
    // 起動時のロゴ演出はアプリを開いて最初の1回だけ
    var introDone by rememberSaveable { mutableStateOf(false) }
    val featured = contents.firstOrNull()
    var detail by remember { mutableStateOf<Content?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBlack)
    ) {
        if (introDone) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // 横向きのスマホは高さが低いので、文字とボタンを小さめにする
                val compact = maxHeight < 480.dp
                val heroHeight = maxHeight - 36.dp   // 下に作品の列の見出しが少しのぞく
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                ) {
                    if (featured != null) {
                        HeroBanner(
                            content = featured,
                            height = heroHeight,
                            compact = compact,
                            onPlay = { onContentSelected(featured) },
                            onInfo = { detail = featured }
                        )
                    }
                    ContentRow(title = "4D で体験できる作品") {
                        items(contents) { c ->
                            PosterCard(content = c, onClick = { onContentSelected(c) })
                        }
                        items(comingSoonList.take(3)) { c -> ComingSoonCard(c) }
                    }
                    ContentRow(title = "近日公開 — COMING SOON") {
                        items(comingSoonList) { c -> ComingSoonCard(c) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
                // スクロールしたら上のバーを黒く塗る（下のカードと文字が重ならないように）
                TopBar(isEffectStationConnected, isActionDriveConnected, solid = (scroll.value / 250f).coerceIn(0f, 1f))
            }
        }

        if (!introDone) {
            IntroLogo(onFinished = { introDone = true })
        }

        detail?.let { c ->
            AlertDialog(
                onDismissRequest = { detail = null },
                containerColor = Color(0xFF181818),
                title = { Text(c.title, color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(c.description, color = HomeGrey)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            c.effectTypes.forEach { EffectChip(it) }
                        }
                        Text(
                            "長さ ${c.durationMs / 60000}:${"%02d".format((c.durationMs / 1000) % 60)}",
                            color = HomeGrey
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { detail = null; onContentSelected(c) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("4D で再生", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { detail = null }) { Text("閉じる", color = HomeGrey) }
                }
            )
        }
    }
}

/** 起動時のロゴ演出（赤い文字が迫ってきて消える） */
@Composable
private fun IntroLogo(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(350))
        glow.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(250)
        // 一気に拡大しながら消える
        val zoom = launch { scale.animateTo(6f, tween(650, easing = FastOutSlowInEasing)) }
        alpha.animateTo(0f, tween(650))
        zoom.join()
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(520.dp, 220.dp)
                .graphicsLayer { this.alpha = glow.value * alpha.value * 0.6f }
                .background(Brush.radialGradient(listOf(HomeRed.copy(alpha = 0.55f), Color.Transparent)))
        )
        Text(
            text = "4D@HOME",
            color = HomeRed,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = 4.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
        )
    }
}

/** 上のバー（ロゴと接続状態）。ヒーローの上に重ねる */
@Composable
private fun TopBar(es: Boolean, ad: Boolean, solid: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomeBlack.copy(alpha = solid))
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "4D@HOME",
            color = HomeRed,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.width(28.dp))
        Text("ホーム", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        Text("映画", color = HomeGrey)
        Spacer(Modifier.width(18.dp))
        Text("近日公開", color = HomeGrey)
        Spacer(Modifier.weight(1f))
        DeviceDot("EffectStation", es)
        Spacer(Modifier.width(14.dp))
        DeviceDot("ActionDrive", ad)
    }
}

@Composable
private fun DeviceDot(label: String, connected: Boolean) {
    // 接続中はゆっくり明滅させる
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val color = if (connected) StatusConnected else Color(0xFF666666)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .graphicsLayer { alpha = if (connected) pulse else 1f }
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (connected) Color.White else HomeGrey, fontSize = 12.sp)
    }
}

/** 大きな看板（ポスター＋タイトル＋再生ボタン） */
@Composable
private fun HeroBanner(
    content: Content,
    height: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit
) {
    val poster = rememberAssetImage(content.posterAssetPath)
    // ポスターがゆっくり寄っていく（ケン・バーンズ）
    val kenBurns by rememberInfiniteTransition(label = "kenburns").animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kenburnsScale"
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()   // 寄っていくポスターが外にはみ出さないように
    ) {
        if (poster != null) {
            Image(
                bitmap = poster,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.55f),   // 顔が並ぶ上の方を見せる
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = kenBurns; scaleY = kenBurns }
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A0000), HomeBlack))))
        }
        // 左から黒く落とす（文字を読みやすく）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        0.75f to Color.Transparent
                    )
                )
        )
        // 下を背景色へ溶かす
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to HomeBlack))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = if (compact) 20.dp else 40.dp)
                .widthIn(max = if (compact) 460.dp else 560.dp)
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(700)) + slideInHorizontally(tween(700)) { -it / 6 }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(HomeRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("4D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("4D@HOME オリジナル体験", color = HomeGrey, fontSize = 13.sp, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = content.englishTitle.ifEmpty { content.title },
                        color = Color.White,
                        fontSize = if (compact) 46.sp else 72.sp,
                        lineHeight = if (compact) 48.sp else 72.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 3.sp
                    )
                    Text(content.title, color = Color.White, fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(700, delayMillis = 250)) + slideInVertically(tween(700, delayMillis = 250)) { it / 3 }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("新着", color = Color(0xFF46D369), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        content.year?.let { Text("   $it", color = HomeGrey, fontSize = 14.sp) }
                        Text("   予告編", color = HomeGrey, fontSize = 14.sp)
                        Text(
                            "   ${content.durationMs / 60000}分${(content.durationMs / 1000) % 60}秒",
                            color = HomeGrey,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        content.effectTypes.forEach { EffectChip(it); Spacer(Modifier.width(6.dp)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        content.description,
                        color = Color(0xFFE5E5E5),
                        fontSize = 15.sp,
                        maxLines = if (compact) 1 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = if (compact) 6.dp else 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("4D で再生", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Button(
                            onClick = onInfo,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6D6D6E).copy(alpha = 0.7f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = if (compact) 6.dp else 12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("詳細情報", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectChip(type: EffectType) {
    val (label, icon) = when (type) {
        EffectType.FAN -> "風" to Icons.Default.Air
        EffectType.SPLASH -> "水" to Icons.Default.WaterDrop
        EffectType.MIST -> "ミスト" to Icons.Default.WaterDrop
        EffectType.LED -> "光" to Icons.Default.Lightbulb
        EffectType.VIBRATION -> "振動" to Icons.Default.Vibration
    }
    Row(
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/** 横に流れる作品の列 */
@Composable
private fun ContentRow(
    title: String,
    items: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 40.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = items
        )
    }
}

/** 押すと少し縮み、離すと戻る */
@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, tween(140), label = "press")
    return s
}

/** 遊べる作品のカード（ポスター） */
@Composable
private fun PosterCard(content: Content, onClick: () -> Unit) {
    val poster = rememberAssetImage(content.posterAssetPath)
    val interaction = remember { MutableInteractionSource() }
    val s = pressScale(interaction)
    Box(
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, HomeRed, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        if (poster != null) {
            Image(poster, contentDescription = content.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF333333)))
        }
        // 左上の「4D」帯
        Box(
            Modifier
                .align(Alignment.TopStart)
                .background(HomeRed)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("4D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        // 下に「新着エピソード」風の帯
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(HomeRed)
                .padding(vertical = 3.dp)
        ) {
            Text(
                "今すぐ体験",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** まだ遊べない作品のカード（光が流れる） */
@Composable
private fun ComingSoonCard(item: ComingSoon) {
    val shine by rememberInfiniteTransition(label = "shine").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "shineX"
    )
    Box(
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(item.colors.reversed()))
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.Center)
        )
        // 斜めに流れる光
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                            start = Offset(w * shine, 0f),
                            end = Offset(w * shine + w * 0.6f, w * 0.9f)
                        )
                    )
            )
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 19.sp)
            Text(item.genre, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("COMING SOON", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
    }
}
