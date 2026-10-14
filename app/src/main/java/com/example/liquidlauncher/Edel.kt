package com.example.liquidlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

/** Material 대화상자(AOD 설정, 위젯 선택 등)도 Edel 색을 따르게 한다 */
val EdelColorScheme = darkColorScheme(
    primary = Edel.Violet,
    onPrimary = Color.White,
    secondary = Edel.Cyan,
    background = Edel.Ink,
    surface = Color(0xFF17172F),
    onSurface = Color(0xFFF3F0FF),
    surfaceVariant = Color(0xFF242444),
    onSurfaceVariant = Color(0xFFCFCBEA)
)

// ───────────────────────── 보석 로고 ─────────────────────────

/** Edel(독일어로 '고귀한') = 보석 모양 마크. 면마다 색이 다르고, 빛이 주기적으로 훑고 지나간다. */
@Composable
fun EdelGem(modifier: Modifier = Modifier, progress: Float = 1f) {
    val t = rememberInfiniteTransition(label = "gem")
    val sweep by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "sweep"
    )
    Canvas(
        modifier.graphicsLayer {
            val s = 0.6f + 0.4f * progress
            scaleX = s
            scaleY = s
            alpha = progress.coerceIn(0f, 1f)
            rotationZ = (1f - progress) * -14f
        }
    ) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        val a = p(0.25f, 0.08f)
        val b = p(0.75f, 0.08f)
        val c = p(1.00f, 0.40f)
        val d = p(0.50f, 1.00f)
        val e = p(0.00f, 0.40f)
        val f = p(0.65f, 0.40f)
        val g = p(0.35f, 0.40f)

        // 1. vararg 대신 Array<Offset>을 받도록 변경된 facet 함수
        fun facet(pts: Array<Offset>): Path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }

        // 뒤쪽 후광
        drawCircle(
            Brush.radialGradient(
                listOf(Edel.Violet.copy(alpha = 0.55f), Edel.Violet.copy(alpha = 0f)),
                center = Offset(w / 2f, h * 0.5f),
                radius = w * 0.95f
            ),
            radius = w * 0.95f,
            center = Offset(w / 2f, h * 0.5f)
        )

        // 2. 각 점들을 arrayOf로 감싼 facets 목록
        val facets = listOf(
            facet(arrayOf(a, b, f, g)) to listOf(Color(0xFFFFFFFF), Color(0xFFCFE6FF)),
            facet(arrayOf(a, g, e)) to listOf(Color(0xFFA6DCFF), Color(0xFFCDB8FF)),
            facet(arrayOf(b, c, f)) to listOf(Color(0xFFBBA8FF), Color(0xFFFF9EE8)),
            facet(arrayOf(e, g, d)) to listOf(Color(0xFF6C8EFF), Color(0xFF8B6CFF)),
            facet(arrayOf(g, f, d)) to listOf(Color(0xFFCBF5FF), Color(0xFF7FDBFF)),
            facet(arrayOf(f, c, d)) to listOf(Color(0xFFA56BFF), Color(0xFFFF6FD8))
        )
        facets.forEach { (path, colors) ->
            drawPath(path, Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(w, h)))
        }
        facets.forEach { (path, _) ->
            drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = w * 0.012f))
        }

        // 빛줄기 (보석 모양으로만 보이게 클립)
        val outline = facet(arrayOf(a, b, c, d, e))
        clipPath(outline) {
            val x = -w * 0.6f + sweep * w * 2.3f
            drawRect(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = 0.62f),
                        Color.White.copy(alpha = 0f)
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x + w * 0.5f, h * 0.5f)
                )
            )
        }
    }
}

/** 부팅(프로세스 시작) 때 한 번 나오는 Edel OS 인트로. 탭하면 건너뛴다. */
@Composable
fun EdelIntro(onDone: () -> Unit) {
    val gem = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { gem.animateTo(1f, iosSpring(0.9f, 0.62f)) }
        delay(380)
        launch { text.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        delay(1500)
        fade.animateTo(0f, tween(480))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(
                Brush.verticalGradient(listOf(Color(0xFF05050F), Color(0xFF1A0F40), Color(0xFF07263F)))
            )
            .pointerInput(Unit) { detectTapGestures { onDone() } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EdelGem(Modifier.size(116.dp), progress = gem.value)
            Spacer(Modifier.size(26.dp))
            Text(
                "EDEL OS",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (18f - 13f * text.value).sp,
                modifier = Modifier.graphicsLayer { alpha = text.value }
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "LAUNCHER",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                letterSpacing = 7.sp,
                modifier = Modifier.graphicsLayer { alpha = text.value * 0.9f }
            )
        }
    }
}

// ───────────────────────── 페이지 표시 ─────────────────────────

/** 현재 위치(position)에 따라 점이 길쭉한 알약으로 늘어나는 페이지 표시 */
@Composable
fun PageDots(count: Int, position: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = ((count - 1) * 14 + 20 + 22).dp, height = 18.dp)
            .glass(ContinuousCornerShape(12.dp), 0.9f)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pos = position()
            val dotH = 6.dp.toPx()
            val cy = size.height / 2f
            var x = 11.dp.toPx()
            for (i in 0 until count) {
                val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
                val w = (6f + 14f * near).dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.35f + 0.6f * near),
                    topLeft = Offset(x, cy - dotH / 2f),
                    size = Size(w, dotH),
                    cornerRadius = CornerRadius(dotH / 2f)
                )
                x += w + 8.dp.toPx()
            }
        }
    }
}

// ───────────────────────── 바텀 시트 ─────────────────────────

data class SheetAction(
    val label: String,
    val glyph: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

data class SheetSpec(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageBitmap? = null,
    val actions: List<SheetAction>
)

private val rowPalette = listOf(
    Edel.Violet to Edel.Indigo,
    Edel.Cyan to Edel.Violet,
    Edel.Rose to Edel.Violet,
    Edel.Gold to Edel.Rose,
    Edel.Indigo to Edel.Cyan
)

/** 아래에서 스프링으로 올라오는 유리 시트. 항목이 차례로 떠오른다. */
@Composable
fun BoxScope.EdelSheet(visible: Boolean, spec: SheetSpec?, onDismiss: () -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(iosSpring(0.5f, 0.82f)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(230)) { it } + fadeOut(tween(180))
    ) {
        if (spec != null) {
            val shape = ContinuousCornerShape(34.dp)
            val maxH = (LocalConfiguration.current.screenHeightDp * 0.74f).dp
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
                    .heightIn(max = maxH)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xF01F1B42), Color(0xF012122B)))
                    )
                    .glass(shape, 0.8f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(Modifier.size(14.dp))
                Row(
                    Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    spec.icon?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(ContinuousCornerShape(46.dp * 0.2237f))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            spec.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        spec.subtitle?.let {
                            Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    spec.actions.forEachIndexed { i, action ->
                        SheetRow(action, i) {
                            onDismiss()
                            action.onClick()
                        }
                        Spacer(Modifier.size(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(action: SheetAction, index: Int, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = iosSpring(0.26f, 0.62f),
        label = "rowPress"
    )
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L + index * 40L)
        enter.animateTo(1f, iosSpring(0.5f, 0.8f))
    }
    val pal = if (action.destructive) Color(0xFFFF5C7A) to Color(0xFFD1264B) else rowPalette[index % rowPalette.size]
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 24.dp.toPx()
                val s = 1f - 0.035f * press
                scaleX = s
                scaleY = s
            }
            .clip(ContinuousCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f + 0.10f * press))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(ContinuousCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(pal.first, pal.second))),
            contentAlignment = Alignment.Center
        ) {
            Text(action.glyph, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            action.label,
            color = if (action.destructive) Color(0xFFFF8CA0) else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
