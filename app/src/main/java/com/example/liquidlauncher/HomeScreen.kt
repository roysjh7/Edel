package com.example.liquidlauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 유리 질감의 앱 아이콘.
 *  - 눌리면 쪼그라들었다가 탄성 있게 돌아옴, 이동 모드에서는 위로 떠오름(그림자가 커짐)
 *  - 위쪽 하이라이트 + 테두리 빛 + 그림자로 입체감을 준다
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppInfo,
    st: LauncherState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 60.dp,
    lifted: Boolean = false,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else if (lifted) 1.12f else 1f,
        animationSpec = iosSpring(0.3f, 0.6f),
        label = "icon"
    )
    val icon = remember(app.key, st.apps, st.iconVersion) { st.repo.icon(app) }
    val shape = remember(iconSize) { ContinuousCornerShape(iconSize * 0.2237f) }
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onLongClick = onLongClick,
                onClick = { onClick(bounds) }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .shadow(
                    elevation = if (lifted) 16.dp else 7.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(shape)
        ) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
            // 유리 느낌의 위쪽 하이라이트
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
            )
            // 가장자리 빛
            Box(
                Modifier.fillMaxSize().border(
                    0.8.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.30f)
                        )
                    ),
                    shape
                )
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
                shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 5f)
            ),
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/** 이동 모드일 때 아이콘이 살짝 떨리는(jiggle) 회전값. 이동 모드가 아니면 0 고정 */
@Composable
private fun rememberJiggle(active: Boolean, periodMs: Int): State<Float> =
    if (active) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -1.8f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
            label = "jiggleValue"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    st: LauncherState,
    pageOffset: () -> Float,
    onLaunch: (AppInfo, Rect) -> Unit,
    onPickIcon: (AppInfo) -> Unit,
    onLongPressEmpty: () -> Unit
) {
    val appMap = remember(st.apps) { st.apps.associateBy { it.key } }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var movingId by remember { mutableStateOf<String?>(null) }

    // 아이콘이 차례로 '톡톡' 튀어나오는 등장 애니메이션 (처음 열릴 때 + 홈 버튼을 다시 누를 때)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(st.homeTick) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(1100, easing = LinearEasing))
    }
    val jigA = rememberJiggle(movingId != null, 120)
    val jigB = rememberJiggle(movingId != null, 150)

    // 화면 모서리 곡률에 맞춰 여백을 잡고, 위젯 곡률은 (화면 곡률 - 여백)으로 동심원이 되게 한다
    val cornerR = LocalScreenCorner.current
    val pad = maxOf(12.dp, cornerR * 0.4f)
    val widgetR = (cornerR - pad).coerceAtLeast(24.dp)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = pad, end = pad, top = 8.dp, bottom = 30.dp)
    ) {
        val cw = maxWidth / COLS
        val ch = maxHeight / ROWS
        val iconSize = minOf(cw * 0.66f, ch * 0.60f)
        // 위젯이 몇 칸을 차지할지 계산할 수 있게 칸 크기(dp)를 알려준다
        SideEffect {
            st.cellWdp = cw.value
            st.cellHdp = ch.value
        }

        // 빈 곳: 탭하면 이동 취소, 길게 누르면 홈 메뉴
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { movingId = null },
                    onLongPress = { onLongPressEmpty() }
                )
            }
        )

        // 마지막 줄은 '독': 배경이 비쳐 보이는 유리 판
        val dockR = minOf(ch * 0.45f, 34.dp)
        Box(
            Modifier
                .offset(0.dp, ch * (ROWS - 1))
                .size(maxWidth, ch)
                .padding(vertical = 3.dp)
                .liquidGlass(ContinuousCornerShape(dockR))
        )

        st.items.forEach { item ->
            key(item.id) {
                val isMoving = movingId == item.id
                Box(
                    Modifier
                        .offset(cw * item.col, ch * item.row)
                        .size(cw * item.spanC, ch * item.spanR)
                        .graphicsLayer {
                            // 등장: 대각선 순서로 튀어나오며 커짐
                            val order = item.col + item.row
                            val p = (enter.value * 1.7f - order * 0.09f).coerceIn(0f, 1f)
                            val s = 0.55f + 0.45f * easeOutBack(p)
                            // 페이지를 넘길 때: 열마다 다르게 벌어지며 작아지는 웨이브
                            val off = pageOffset()
                            val k = s * (1f - 0.18f * off)
                            scaleX = k
                            scaleY = k
                            alpha = p
                            translationY = (1f - p) * 36f
                            translationX = -off * (item.col - 1.5f) * 34f
                            if (movingId != null && !isMoving) {
                                rotationZ = if ((item.col + item.row) % 2 == 0) jigA.value else jigB.value
                            }
                        }
                ) {
                    when (item.type) {
                        "app" -> {
                            val app = appMap[item.payload]
                            if (app != null) {
                                AppCell(
                                    app, st, Modifier.fillMaxSize(),
                                    iconSize = iconSize,
                                    lifted = isMoving,
                                    onClick = { r ->
                                        if (movingId == null) onLaunch(app, r) else movingId = null
                                    },
                                    onLongClick = { menuFor = item.id }
                                )
                            }
                        }
                        "widget" -> {
                            WidgetView(
                                st, item, cw * item.spanC, ch * item.spanR,
                                Modifier.fillMaxSize(),
                                onLongPress = { menuFor = item.id }
                            )
                        }
                        "clock" -> {
                            ClockWidget(
                                Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .combinedClickable(onLongClick = { menuFor = item.id }, onClick = {}),
                                cornerRadius = widgetR
                            )
                        }
                    }
                    val actions = buildList {
                        add(MenuAction("이동") { movingId = item.id; menuFor = null })
                        if (item.type == "app") {
                            val app = appMap[item.payload]
                            if (app != null) {
                                add(MenuAction("아이콘 변경") { onPickIcon(app); menuFor = null })
                                add(MenuAction("기본 아이콘으로") { st.clearCustomIcon(app); menuFor = null })
                            }
                        }
                        add(MenuAction("홈에서 제거", destructive = true) { st.remove(item.id); menuFor = null })
                    }
                    GlassMenu(
                        expanded = menuFor == item.id,
                        onDismiss = { menuFor = null },
                        actions = actions
                    )
                }
            }
        }

        // 이동 모드: 모든 칸을 은은한 유리 칸으로 보여주고, 탭한 칸으로 옮긴다
        val mv = movingId
        if (mv != null) {
            val cellShape = ContinuousCornerShape(14.dp)
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                Box(
                    Modifier
                        .offset(cw * c, ch * r)
                        .size(cw, ch)
                        .padding(3.dp)
                        .clip(cellShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), cellShape)
                        .tap {
                            st.move(mv, c, r)
                            movingId = null
                        }
                )
            }
            Text(
                "옮길 칸을 탭하세요",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(ContinuousCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

/** 기본 제공 시계 위젯: 유리 판 + 부드러운 그라데이션 글자 + 깜빡이는 콜론 */
@Composable
fun ClockWidget(modifier: Modifier = Modifier, cornerRadius: Dp = 28.dp) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val hh = remember(now) { SimpleDateFormat("H", Locale.getDefault()).format(now) }
    val mm = remember(now) { SimpleDateFormat("mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("M월 d일 EEEE", Locale.getDefault()).format(now) }
    val blink = rememberInfiniteTransition(label = "colon").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "colonAlpha"
    )
    val big = TextStyle(
        brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.72f))),
        fontSize = 64.sp,
        fontWeight = FontWeight.Light
    )
    Box(modifier.liquidGlass(ContinuousCornerShape(cornerRadius)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hh, style = big)
                Text(":", style = big, modifier = Modifier.graphicsLayer { alpha = blink.value })
                Text(mm, style = big)
            }
            Text(date, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
    }
}
