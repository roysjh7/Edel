package com.example.liquidlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs

/** 눌림 효과 없이 탭만 받는 클릭 (기본 리플 제거) */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/** 끝에서 살짝 넘쳤다가 돌아오는 이징 (아이콘이 '톡' 튀어나오는 느낌) */
fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

// ─────────────────────────── 컨텍스트 메뉴 (길게 누르면) ───────────────────────────

data class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

private class MenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val margin = 24
        val gap = 12
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height - margin) {
            y = anchorBounds.top - popupContentSize.height - gap
        }
        return IntOffset(x, y.coerceAtLeast(margin))
    }
}

/** iOS 컨텍스트 메뉴처럼 아이콘 근처에서 탄성 있게 펼쳐지는 메뉴 */
@Composable
fun GlassMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    if (expanded) {
        val provider = remember { MenuPositionProvider() }
        Popup(
            popupPositionProvider = provider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true)
        ) {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            AnimatedVisibility(
                visible = shown,
                enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                    initialScale = 0.5f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                ) + fadeIn(tween(120))
            ) {
                Column(
                    Modifier
                        .width(212.dp)
                        .flatGlass(ContinuousCornerShape(20.dp))
                        .padding(vertical = 6.dp)
                ) {
                    actions.forEachIndexed { i, a ->
                        if (i > 0) {
                            Box(
                                Modifier.fillMaxWidth().height(0.5.dp)
                                    .background(Color.White.copy(alpha = 0.12f))
                            )
                        }
                        Text(
                            a.label,
                            color = if (a.destructive) Color(0xFFFF6B6B) else Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tap { a.onClick() }
                                .padding(horizontal = 18.dp, vertical = 13.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 아래에서 올라오는 시트 ───────────────────────────

/**
 * 화면 안에 그려지는 유리 시트 (다이얼로그 창이 아니라서 배경이 비쳐 보인다).
 * 아래에서 탄성 있게 올라오고, 뒷배경은 어두워진다.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible) { onDismiss() }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .liquidGlass(ContinuousCornerShape(36.dp), strength = 1.3f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .heightIn(max = maxH)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                content()
            }
        }
    }
}

@Composable
fun SheetRow(
    label: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
        tween(120),
        label = "row"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(18.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (destructive) Color(0xFFFF6B6B) else Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 16.sp
            )
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 22.sp)
    }
}

@Composable
fun SheetSwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        IosSwitch(checked, enabled, onChange)
    }
}

@Composable
fun SheetChips(
    label: String,
    options: List<Int>,
    selected: Int,
    fmt: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                val sel = o == selected
                val bg by animateColorAsState(
                    if (sel) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f),
                    tween(180),
                    label = "chip"
                )
                Box(
                    Modifier
                        .clip(ContinuousCornerShape(12.dp))
                        .background(bg)
                        .tap { onSelect(o) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        fmt(o),
                        color = if (sel) Color(0xFF111118) else Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SheetNote(text: String, color: Color = Color.White.copy(alpha = 0.65f)) {
    Text(text, color = color, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

/** iOS 스타일 스위치 (색이 부드럽게 바뀌고 손잡이가 탄성 있게 움직인다) */
@Composable
fun IosSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(
        if (checked) Color(0xFF34C759) else Color.White.copy(alpha = 0.25f),
        tween(220),
        label = "track"
    )
    val knobX by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "knob"
    )
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .tap(enabled = enabled) { onChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

// ─────────────────────────── 페이지 점 ───────────────────────────

/** 현재 페이지가 부드럽게 커지는 점 표시. progress 는 0..(count-1) 사이의 실수 */
@Composable
fun PageDots(count: Int, progress: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = (count * 16).dp, height = 12.dp)) {
        val p = progress()
        for (i in 0 until count) {
            val d = (1f - abs(p - i)).coerceIn(0f, 1f)
            val r = (3.0f + 1.8f * d).dp.toPx()
            drawCircle(
                Color.White.copy(alpha = 0.35f + 0.6f * d),
                radius = r,
                center = Offset(size.width / count * (i + 0.5f), size.height / 2f)
            )
        }
    }
}
