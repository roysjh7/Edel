package com.example.liquidlauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** 반투명 유리 질감(그라데이션 + 하이라이트 테두리) */
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

/** 배경화면 + 천천히 흐르는 액체 빛 번짐 + 어두운 스크림 */
@Composable
fun Backdrop(wallpaper: ImageBitmap?, dim: Float = 0.15f, animated: Boolean = true) {
    Box(Modifier.fillMaxSize()) {
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
                            listOf(Color(0xFF1B1F4B), Color(0xFF3B1E5E), Color(0xFF0E4D64))
                        )
                    )
            )
        }
        if (animated) LiquidBlobs()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

@Composable
fun LiquidBlobs() {
    val t = rememberInfiniteTransition(label = "blobs")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "phase"
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val blobs = listOf(
            Triple(Color(0xFF7C9CFF), 0.0f, 0.35f),
            Triple(Color(0xFFFF8AD8), 2.1f, 0.30f),
            Triple(Color(0xFF6FFFE9), 4.2f, 0.28f)
        )
        blobs.forEach { (c, off, r) ->
            val cx = w * (0.5f + 0.35f * sin(phase + off))
            val cy = h * (0.5f + 0.30f * cos(phase * 0.8f + off * 1.3f))
            val radius = w * r * 1.6f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
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
