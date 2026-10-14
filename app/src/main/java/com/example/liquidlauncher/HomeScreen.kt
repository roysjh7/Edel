package com.example.liquidlauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WhiteClear = Color.White.copy(alpha = 0f)

/** 이동 모드에서 아이콘이 살짝 떨리는 각도(도). 비활성이면 0 고정 (애니메이션 자체를 만들지 않는다) */
@Composable
fun rememberJiggle(active: Boolean): State<Float> {
    if (!active) return remember { mutableStateOf(0f) }
    val t = rememberInfiniteTransition(label = "jiggle")
    return t.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(110, easing = LinearEasing), RepeatMode.Reverse),
        label = "jiggleAngle"
    )
}

/**
 * 앱 아이콘 + 라벨.
 *  - 등장: 스프링으로 튀어 오르며 나타남 (index 순서대로 물결처럼, enterKey 가 바뀌면 다시 재생)
 *  - 눌림: 스프링으로 작아지며 안쪽이 빛남, 길게 누르면 햅틱
 *  - 모양: iOS 연속 곡률 + 바닥 그림자 + 윗면 광택 + 가는 하이라이트 테두리
 *  - onClick 에는 화면상 아이콘 위치(Rect)를 넘겨서 '아이콘에서 확대되며 열리는' 전환에 쓴다
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppInfo,
    st: LauncherState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 60.dp,
    index: Int = 0,
    enterKey: Int = 0,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = iosSpring(0.28f, 0.6f),
        label = "press"
    )
    val enter = remember { Animatable(0f) }
    LaunchedEffect(enterKey) {
        enter.snapTo(0f)
        delay(index * 14L)
        enter.animateTo(1f, iosSpring(0.6f, 0.68f))
    }
    val haptic = LocalHapticFeedback.current
    val icon = remember(app.key, st.apps, st.iconVersion) { st.repo.icon(app) }
    val bounds = remember { arrayOf(Rect.Zero) } // 상태가 아니라 단순 보관(스크롤 중 재구성 방지)
    val shape = remember(iconSize) { ContinuousCornerShape(iconSize * 0.2237f) }

    Column(
        modifier
            .graphicsLayer {
                val e = enter.value
                val s = (0.55f + 0.45f * e) * (1f - 0.12f * press)
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 18.dp.toPx()
            }
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = { onClick(bounds[0]) }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
                .drawBehind {
                    // 아이콘 아래 부드러운 바닥 그림자
                    val w = size.width
                    val h = size.height
                    val c = Offset(w / 2f, h * 0.87f)
                    drawOval(
                        Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0f)),
                            center = c,
                            radius = w * 0.62f
                        ),
                        topLeft = Offset(-w * 0.08f, h * 0.62f),
                        size = Size(w * 1.16f, h * 0.5f)
                    )
                }
                .clip(shape)
        ) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize()
            )
            // 윗면 광택 + 하이라이트 테두리
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.17f),
                            0.5f to WhiteClear
                        )
                    )
                    .border(
                        0.75.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.04f),
                                Color.White.copy(alpha = 0.16f)
                            )
                        ),
                        shape
                    )
            )
            // 눌렸을 때 안쪽이 빛남
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = press * 0.22f }
                    .background(Color.White)
            )
        }
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), 5f)
            ),
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    st: LauncherState,
    enterKey: Int,
    onLaunch: (AppInfo, Rect) -> Unit,
    onPickIcon: (AppInfo) -> Unit,
    onLongPressEmpty: () -> Unit,
    onShowSheet: (SheetSpec) -> Unit
) {
    val appMap = remember(st.apps) { st.apps.associateBy { it.key } }
    var movingId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 26.dp)
    ) {
        val cw = maxWidth / COLS
        val ch = maxHeight / ROWS
        // iOS 홈 화면 비율: 아이콘 ≈ 칸 너비의 64%
        val iconSize = minOf(cw * 0.64f, ch * 0.58f)
        // 위젯이 몇 칸을 차지할지 계산할 수 있게 칸 크기(dp)를 알려준다
        SideEffect {
            st.cellWdp = cw.value
            st.cellHdp = ch.value
        }

        // 맨 아랫줄 뒤의 유리 독(dock)
        Box(
            Modifier
                .offset(0.dp, ch * (ROWS - 1))
                .size(maxWidth, ch)
                .padding(vertical = 4.dp)
                .glass(ContinuousCornerShape(30.dp), 0.8f)
        )

        // 빈 곳: 탭하면 이동 취소, 길게 누르면 홈 메뉴
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { movingId = null },
                    onLongPress = { onLongPressEmpty() }
                )
            }
        )

        st.items.forEach { item ->
            key(item.id) {
                // 이동하면 제자리에서 튀지 않고 스프링으로 미끄러진다
                val ox by animateDpAsState(cw * item.col, iosSpring(0.5f, 0.8f), label = "ox")
                val oy by animateDpAsState(ch * item.row, iosSpring(0.5f, 0.8f), label = "oy")
                val moving = movingId == item.id
                val jiggle = rememberJiggle(moving)
                val liftScale by animateFloatAsState(
                    if (moving) 1.07f else 1f, iosSpring(0.35f, 0.65f), label = "lift"
                )
                val app = if (item.type == "app") appMap[item.payload] else null
                val haptic = LocalHapticFeedback.current

                fun openMenu() {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val actions = buildList {
                        if (app != null) {
                            add(SheetAction("아이콘 변경", "🎨") { onPickIcon(app) })
                            add(SheetAction("기본 아이콘으로", "🔄") { st.clearCustomIcon(app) })
                        }
                        add(SheetAction("이동", "✋") { movingId = item.id })
                        add(SheetAction("홈에서 제거", "🗑", destructive = true) { st.remove(item.id) })
                    }
                    onShowSheet(
                        SheetSpec(
                            title = app?.label ?: if (item.type == "clock") "시계 위젯" else "위젯",
                            subtitle = "홈 화면",
                            icon = app?.let { st.repo.icon(it) },
                            actions = actions
                        )
                    )
                }

                Box(
                    Modifier
                        .offset(ox, oy)
                        .size(cw * item.spanC, ch * item.spanR)
                        .graphicsLayer {
                            rotationZ = jiggle.value
                            scaleX = liftScale
                            scaleY = liftScale
                        }
                ) {
                    when (item.type) {
                        "app" -> {
                            if (app != null) {
                                AppCell(
                                    app, st, Modifier.fillMaxSize(),
                                    iconSize = iconSize,
                                    index = item.row * COLS + item.col,
                                    enterKey = enterKey,
                                    onClick = { r ->
                                        if (movingId == null) onLaunch(app, r) else movingId = null
                                    },
                                    onLongClick = { openMenu() }
                                )
                            }
                        }
                        "widget" -> {
                            WidgetView(
                                st, item, cw * item.spanC, ch * item.spanR,
                                Modifier.fillMaxSize(),
                                onLongPress = { openMenu() }
                            )
                        }
                        "clock" -> {
                            ClockWidget(
                                Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .combinedClickable(onLongClick = { openMenu() }, onClick = {})
                            )
                        }
                    }
                }
            }
        }

        // 이동 모드: 모든 칸을 숨 쉬듯 반짝이는 탭 대상으로 표시
        val mv = movingId
        if (mv != null) {
            val pulse = rememberInfiniteTransition(label = "grid")
            val glow by pulse.animateFloat(
                initialValue = 0.18f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "glow"
            )
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                Box(
                    Modifier
                        .offset(cw * c, ch * r)
                        .size(cw, ch)
                        .padding(3.dp)
                        .border(1.dp, Color.White.copy(alpha = glow), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            st.move(mv, c, r)
                            movingId = null
                        }
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .glass(ContinuousCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("옮길 칸을 탭하세요", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 시계 위젯: 유리 카드 + 그라데이션 글자 + 깜박이는 콜론 + 주기적으로 지나가는 빛 */
@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val hh = remember(now) { SimpleDateFormat("HH", Locale.getDefault()).format(now) }
    val mm = remember(now) { SimpleDateFormat("mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("M월 d일 EEEE", Locale.getDefault()).format(now) }
    val blink = rememberInfiniteTransition(label = "colon")
    val colon by blink.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "colonAlpha"
    )
    val radius = (LocalScreenCorner.current - 16.dp).coerceAtLeast(20.dp)
    val timeStyle = TextStyle(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFD8CFFF))),
        fontSize = 60.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 1.sp
    )
    Box(
        modifier
            .glass(ContinuousCornerShape(radius), 1.1f)
            .sheen(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hh, style = timeStyle)
                Text(":", style = timeStyle, modifier = Modifier.graphicsLayer { alpha = colon })
                Text(mm, style = timeStyle)
            }
            Text(
                date,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        }
    }
}
