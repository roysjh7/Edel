package com.example.liquidlauncher

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────
//  배경 뒤 '흐린 배경화면' (유리 패널이 비쳐 보이는 용도)
// ─────────────────────────────────────────────────────────────

/** 화면 크기에 맞춰 잘라서 미리 흐리게 만든 배경 이미지 */
class BackdropData(val blurred: ImageBitmap, val width: Int, val height: Int)

val LocalBackdrop = compositionLocalOf<BackdropData?> { null }

/** 유리 위를 지나가는 빛 띠의 진행도(0~1). State 를 그대로 내려서 재구성 없이 그리기 단계에서만 읽는다 */
val LocalSweep = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** 배경화면이 없을 때 쓰는 기본 배경 (어두운 보라-청록 + 은은한 빛무리) */
fun defaultWallpaperBitmap(w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(0xFF141A45.toInt(), 0xFF3A1D63.toInt(), 0xFF0B4A66.toInt()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    fun glow(cx: Float, cy: Float, r: Float, color: Int) {
        val q = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
        q.shader = RadialGradient(cx, cy, r, color, 0x00000000, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, q)
    }
    glow(w * 0.20f, h * 0.18f, w * 0.75f, 0x667C9CFF)
    glow(w * 0.85f, h * 0.50f, w * 0.85f, 0x55FF8AD8)
    glow(w * 0.30f, h * 0.90f, w * 0.95f, 0x556FFFE9)
    return bmp
}

private fun blur1D(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val n = 2 * r + 1
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val xx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                val yy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                val px = src[yy * w + xx]
                sa += px ushr 24
                sr += (px shr 16) and 0xFF
                sg += (px shr 8) and 0xFF
                sb += px and 0xFF
            }
            dst[y * w + x] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
        }
    }
}

private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
    val w = src.width
    val h = src.height
    var a = IntArray(w * h)
    src.getPixels(a, 0, w, 0, 0, w, h)
    var b = IntArray(w * h)
    repeat(passes) {
        blur1D(a, b, w, h, radius, true)
        blur1D(b, a, w, h, radius, false)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

fun makeBackdrop(wallpaper: ImageBitmap?, w: Int, h: Int): BackdropData {
    val src: Bitmap = wallpaper?.asAndroidBitmap() ?: defaultWallpaperBitmap(360, 780)
    val targetRatio = w.toFloat() / h.toFloat()
    val srcRatio = src.width.toFloat() / src.height.toFloat()
    val cw: Int
    val ch: Int
    if (srcRatio > targetRatio) {
        ch = src.height
        cw = (src.height * targetRatio).toInt().coerceIn(1, src.width)
    } else {
        cw = src.width
        ch = (src.width / targetRatio).toInt().coerceIn(1, src.height)
    }
    val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
    val tw = 54
    val th = (tw / targetRatio).toInt().coerceAtLeast(1)
    var cur = cropped
    while (cur.width / 2 > tw && cur.height / 2 > th) {
        cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
    }
    val small = Bitmap.createScaledBitmap(cur, tw, th, true)
    return BackdropData(boxBlur(small, 2, 3).asImageBitmap(), w, h)
}

/**
 * 화면 전체를 감싸서 '흐린 배경'을 만들어 CompositionLocal 로 내려준다.
 * animate=false 면 빛 띠 애니메이션을 멈춘다 (대기 화면용).
 */
@Composable
fun BackdropProvider(wallpaper: ImageBitmap?, animate: Boolean = true, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        val data by produceState<BackdropData?>(null, wallpaper, wPx, hPx) {
            value = withContext(Dispatchers.Default) { makeBackdrop(wallpaper, wPx, hPx) }
        }
        val transition = rememberInfiniteTransition(label = "sweep")
        val sweep: State<Float> = if (animate) {
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 9000
                        0f at 0
                        1f at 2400
                        1f at 9000
                    }
                ),
                label = "sweepValue"
            )
        } else {
            remember { mutableStateOf(0f) }
        }
        CompositionLocalProvider(LocalBackdrop provides data, LocalSweep provides sweep) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  리퀴드 글래스 질감
// ─────────────────────────────────────────────────────────────

/**
 * 진짜 '비치는' 유리: 같은 위치의 흐린 배경화면을 그 자리에 그리고,
 * 위에서 아래로 번지는 하이라이트 + 테두리 빛 + 주기적으로 지나가는 반사 띠를 얹는다.
 * (별도 창인 팝업/다이얼로그 안에서는 위치가 어긋나므로 flatGlass 를 쓴다)
 */
fun Modifier.liquidGlass(shape: Shape, strength: Float = 1f, tint: Color = Color.Transparent): Modifier = composed {
    val bd = LocalBackdrop.current
    val sweep = LocalSweep.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (bd != null) {
                drawImage(
                    bd.blurred,
                    dstOffset = IntOffset((-pos.x).roundToInt(), (-pos.y).roundToInt()),
                    dstSize = IntSize(bd.width, bd.height),
                    filterQuality = FilterQuality.Medium
                )
            } else {
                drawRect(Color(0xFF20243A))
            }
            drawRect(Color.Black.copy(alpha = 0.14f * strength))
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.24f * strength),
                        Color.White.copy(alpha = 0.05f * strength)
                    )
                )
            )
            if (tint != Color.Transparent) drawRect(tint)
            val t = sweep.value
            if (t > 0f && t < 1f) {
                val x = size.width * (-0.4f + 1.8f * t)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                        start = Offset(x - size.width * 0.18f, 0f),
                        end = Offset(x + size.width * 0.18f, size.height * 0.45f)
                    )
                )
            }
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.80f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.35f)
                )
            ),
            shape
        )
}

/** 별도 창(Popup 등) 안에서 쓰는 단순 반투명 유리 */
fun Modifier.flatGlass(shape: Shape): Modifier =
    this
        .clip(shape)
        .background(Color(0xE61B1D26))
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.20f)
                )
            ),
            shape
        )

/** 앱서랍처럼 '화면 전체를 흐린 배경화면으로 덮는' 용도. 화면에 고정된 것처럼 보여 시차 효과가 난다 */
fun Modifier.backdropBlurFill(darken: Float): Modifier = composed {
    val bd = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            if (bd != null) {
                drawImage(
                    bd.blurred,
                    dstOffset = IntOffset((-pos.x).roundToInt(), (-pos.y).roundToInt()),
                    dstSize = IntSize(bd.width, bd.height),
                    filterQuality = FilterQuality.Medium
                )
            }
            drawRect(Color.Black.copy(alpha = darken))
        }
}

/** 예전 방식의 단순 유리 (호환용) */
fun Modifier.glass(shape: Shape = ContinuousCornerShape(24.dp), strength: Float = 1f): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.26f * strength),
                    Color.White.copy(alpha = 0.08f * strength)
                )
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.35f)
                )
            ),
            shape
        )

// ─────────────────────────────────────────────────────────────
//  배경 (배경화면 + 천천히 흐르는 오로라 + 비네팅)
// ─────────────────────────────────────────────────────────────

@Composable
fun Backdrop(wallpaper: ImageBitmap?, dim: Float = 0.12f, animated: Boolean = true) {
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Aurora(alphaScale = 0.10f, animated = animated)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(Color(0xFF141A45), Color(0xFF3A1D63), Color(0xFF0B4A66))
                    )
                )
            )
            Aurora(alphaScale = 0.34f, animated = animated)
        }
        // 위/아래를 살짝 어둡게 (상태바·독 글자 가독성)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.34f)
                    )
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

@Composable
fun Aurora(alphaScale: Float, animated: Boolean) {
    val phase: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
            label = "auroraPhase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = phase.value
        val blobs = listOf(
            Triple(Color(0xFF7C9CFF), 0.0f, 0.62f),
            Triple(Color(0xFFFF8AD8), 2.1f, 0.55f),
            Triple(Color(0xFF6FFFE9), 4.2f, 0.58f),
            Triple(Color(0xFFB388FF), 5.3f, 0.50f)
        )
        blobs.forEach { (c, off, r) ->
            val cx = w * (0.5f + 0.38f * sin(p + off))
            val cy = h * (0.5f + 0.34f * cos(p * 0.8f + off * 1.3f))
            val radius = min(w, h) * r * 1.5f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.55f * alphaScale), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy),
                blendMode = BlendMode.Screen
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
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = iosSpring(0.28f, 0.6f),
        label = "btn"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(ContinuousCornerShape(20.dp))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 15.sp
        )
    }
}
