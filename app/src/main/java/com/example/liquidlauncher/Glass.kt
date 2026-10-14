package com.example.liquidlauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Edel OS 팔레트 */
object Edel {
    val Ink = Color(0xFF070716)
    val Indigo = Color(0xFF4B3CFF)
    val Violet = Color(0xFF8B6CFF)
    val Cyan = Color(0xFF45E3FF)
    val Rose = Color(0xFFFF6FD8)
    val Gold = Color(0xFFFFD98A)
}

// 같은 색의 투명값 (Color.Transparent 는 검정이라 그라데이션 중간이 탁해진다)
private val WhiteClear = Color.White.copy(alpha = 0f)
private val BlackClear = Color.Black.copy(alpha = 0f)

/**
 * Edel 유리 질감: 반투명 채움 + 윗면 반사광 + 아랫면 안쪽 그림자 + 빛나는 테두리.
 */
fun Modifier.glass(
    shape: Shape = ContinuousCornerShape(24.dp),
    strength: Float = 1f,
    tint: Color = Color.White
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = 0.24f * strength),
                tint.copy(alpha = 0.07f * strength)
            )
        )
    )
    .drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        // 윗면 반사광
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.20f * strength),
                1f to WhiteClear,
                startY = 0f,
                endY = h * 0.5f
            ),
            size = Size(w, h * 0.5f)
        )
        // 아랫면 안쪽 그림자
        drawRect(
            Brush.verticalGradient(
                0f to BlackClear,
                1f to Color.Black.copy(alpha = 0.12f * strength),
                startY = h * 0.6f,
                endY = h
            ),
            topLeft = Offset(0f, h * 0.6f),
            size = Size(w, h * 0.4f)
        )
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.75f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.38f)
            )
        ),
        shape
    )

/** 유리 위를 주기적으로 쓸고 지나가는 빛줄기 */
@Composable
fun Modifier.sheen(periodMs: Int = 6500): Modifier {
    val t = rememberInfiniteTransition(label = "sheen")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "p"
    )
    return this.drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        val x = -w * 0.6f + p * w * 2.2f
        drawRect(
            Brush.linearGradient(
                listOf(WhiteClear, Color.White.copy(alpha = 0.17f), WhiteClear),
                start = Offset(x, 0f),
                end = Offset(x + w * 0.45f, h * 0.45f)
            )
        )
    }
}

/**
 * 배경: 배경화면(또는 Edel 기본 하늘) + 천천히 흐르는 오로라 + 비네트.
 *  - parallax: 페이지 위치(0=홈, 1=서랍). 배경이 살짝 따라 움직인다
 *  - blurPx: 서랍 쪽으로 갈수록 배경을 흐리게 (Android 12+ 에서만 적용)
 */
@Composable
fun Backdrop(
    wallpaper: ImageBitmap?,
    dim: Float = 0.15f,
    animated: Boolean = true,
    parallax: () -> Float = { 0f },
    blurPx: () -> Float = { 0f }
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                    translationX = -parallax() * size.width * 0.05f
                    val b = blurPx()
                    renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Clamp) else null
                }
        ) {
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF090920), Color(0xFF231250), Color(0xFF0A3454))
                            )
                        )
                )
            }
            if (animated) EdelAurora(subtle = wallpaper != null)
            if (animated && wallpaper == null) EdelStars()
        }
        // 비네트: 가장자리를 눌러 아이콘 라벨이 잘 보이게
        Box(
            Modifier.fillMaxSize().drawBehind {
                val r = max(size.width, size.height) * 0.78f
                drawRect(
                    Brush.radialGradient(
                        listOf(BlackClear, Color.Black.copy(alpha = 0.38f)),
                        center = Offset(size.width / 2f, size.height * 0.45f),
                        radius = r
                    )
                )
            }
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

private class Blob(val color: Color, val off: Float, val sx: Int, val sy: Int, val r: Float)

private val auroraBlobs = listOf(
    Blob(Edel.Violet, 0.0f, 1, 1, 0.95f),
    Blob(Edel.Rose, 2.1f, 2, 1, 0.80f),
    Blob(Edel.Cyan, 4.0f, 1, 2, 0.85f),
    Blob(Edel.Indigo, 5.3f, 2, 3, 0.70f)
)

@Composable
private fun EdelAurora(subtle: Boolean) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "phase"
    )
    val a = if (subtle) 0.20f else 0.46f
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ph = phase
        auroraBlobs.forEach { b ->
            val cx = w * (0.5f + 0.40f * sin(ph * b.sx + b.off))
            val cy = h * (0.5f + 0.34f * cos(ph * b.sy + b.off * 1.3f))
            val r = w * b.r
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(b.color.copy(alpha = a), b.color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }
    }
}

private class Star(val x: Float, val y: Float, val r: Float, val k: Int, val o: Float)

@Composable
private fun EdelStars() {
    val stars = remember {
        val rnd = Random(11)
        List(34) {
            Star(
                rnd.nextFloat(), rnd.nextFloat(),
                0.7f + rnd.nextFloat() * 1.5f,
                1 + rnd.nextInt(3),
                rnd.nextFloat() * 6.28f
            )
        }
    }
    val t = rememberInfiniteTransition(label = "stars")
    val ph by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "twinkle"
    )
    Canvas(Modifier.fillMaxSize()) {
        val p = ph
        stars.forEach { s ->
            val tw = 0.5f + 0.5f * sin(p * s.k + s.o)
            drawCircle(
                Color.White.copy(alpha = 0.08f + 0.55f * tw),
                radius = s.r * density,
                center = Offset(s.x * size.width, s.y * size.height)
            )
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = iosSpring(0.28f, 0.6f),
        label = "btn"
    )
    Box(
        modifier
            .graphicsLayer {
                val s = 1f - 0.05f * press
                scaleX = s
                scaleY = s
            }
            .glass(ContinuousCornerShape(20.dp), 1f + 0.5f * press)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
