package com.example.liquidlauncher

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * SwiftUI 의 spring(response:dampingFraction:) 과 같은 감각의 스프링.
 * Compose 스프링은 질량 1 기준이라 stiffness = (2π / response)^2.
 *  - 부드럽게 멈추는 느낌: response 0.45~0.5, damping 0.85~1.0
 *  - 눌림/탭 반응:        response 0.25~0.3, damping 0.55~0.65
 */
fun <T> iosSpring(response: Float = 0.5f, damping: Float = 1f): SpringSpec<T> =
    spring(
        dampingRatio = damping,
        stiffness = ((2.0 * PI / response).pow(2.0)).toFloat()
    )

/** 실제 화면 모서리 반지름 (Android 12+ 에서 측정, 그 전에는 기본값) */
val LocalScreenCorner = compositionLocalOf { 40.dp }

/**
 * iOS 의 '연속 곡률(continuous corner)' 모서리.
 * 일반 원호 대신 슈퍼타원(n=4) 코너를 써서 직선 → 곡선 전환이 부드럽다.
 * radius 는 원호 모서리와 비슷한 체감 크기로 맞춰 둔 값.
 */
class ContinuousCornerShape(
    private val radius: Dp,
    private val n: Float = 4f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }
        val e = min(r * 1.7f, min(w, h) / 2f)
        val path = Path()
        if (e <= 0f || w <= 0f || h <= 0f) {
            path.addRect(Rect(0f, 0f, w, h))
            return Outline.Generic(path)
        }
        val steps = 18
        val p = 2f / n
        val half = (PI / 2).toFloat()
        var first = true
        fun pt(x: Float, y: Float) {
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        fun c(t: Float) = cos(t).coerceAtLeast(0f).pow(p)
        fun s(t: Float) = sin(t).coerceAtLeast(0f).pow(p)
        // 좌상 (왼쪽 변 → 위쪽 변)
        for (i in 0..steps) { val t = half * i / steps; pt(e - e * c(t), e - e * s(t)) }
        // 우상 (위쪽 변 → 오른쪽 변)
        for (i in 0..steps) { val t = half * i / steps; pt(w - e + e * s(t), e - e * c(t)) }
        // 우하 (오른쪽 변 → 아래쪽 변)
        for (i in 0..steps) { val t = half * i / steps; pt(w - e + e * c(t), h - e + e * s(t)) }
        // 좌하 (아래쪽 변 → 왼쪽 변)
        for (i in 0..steps) { val t = half * i / steps; pt(e - e * s(t), h - e + e * c(t)) }
        path.close()
        return Outline.Generic(path)
    }
}
