package com.example.liquidlauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱 아이콘 + 라벨.
 *  - 아이콘은 iOS 연속 곡률 모양(반지름 = 크기의 22.37%)으로 잘라 표시
 *  - 눌림은 SwiftUI 느낌의 스프링 (살짝 튕김)
 *  - onClick 에는 화면상 아이콘 위치(Rect)를 넘겨서 '아이콘에서 확대되며 열리는' 전환에 쓴다
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppInfo,
    st: LauncherState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 60.dp,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = iosSpring(0.28f, 0.6f),
        label = "icon"
    )
    val icon = remember(app.key, st.apps, st.iconVersion) { st.repo.icon(app) }
    val bounds = remember { arrayOf(Rect.Zero) } // 상태가 아니라 단순 보관(스크롤 중 재구성 방지)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onLongClick = onLongClick,
                onClick = { onClick(bounds[0]) }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            bitmap = icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
                .clip(ContinuousCornerShape(iconSize * 0.2237f))
        )
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = 11.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)
            ),
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    st: LauncherState,
    onLaunch: (AppInfo, Rect) -> Unit,
    onPickIcon: (AppInfo) -> Unit,
    onLongPressEmpty: () -> Unit
) {
    val appMap = remember(st.apps) { st.apps.associateBy { it.key } }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var movingId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
                Box(
                    Modifier
                        .offset(cw * item.col, ch * item.row)
                        .size(cw * item.spanC, ch * item.spanR)
                ) {
                    when (item.type) {
                        "app" -> {
                            val app = appMap[item.payload]
                            if (app != null) {
                                AppCell(
                                    app, st, Modifier.fillMaxSize(),
                                    iconSize = iconSize,
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
                                    .combinedClickable(onLongClick = { menuFor = item.id }, onClick = {})
                            )
                        }
                    }
                    DropdownMenu(expanded = menuFor == item.id, onDismissRequest = { menuFor = null }) {
                        if (item.type == "app") {
                            val app = appMap[item.payload]
                            if (app != null) {
                                DropdownMenuItem(
                                    text = { Text("아이콘 변경") },
                                    onClick = { onPickIcon(app); menuFor = null }
                                )
                                DropdownMenuItem(
                                    text = { Text("기본 아이콘으로") },
                                    onClick = { st.clearCustomIcon(app); menuFor = null }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("이동") },
                            onClick = { movingId = item.id; menuFor = null }
                        )
                        DropdownMenuItem(
                            text = { Text("홈에서 제거") },
                            onClick = { st.remove(item.id); menuFor = null }
                        )
                    }
                }
            }
        }

        // 이동 모드: 모든 칸을 탭 대상으로 표시
        val mv = movingId
        if (mv != null) {
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                Box(
                    Modifier
                        .offset(cw * c, ch * r)
                        .size(cw, ch)
                        .padding(3.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable {
                            st.move(mv, c, r)
                            movingId = null
                        }
                )
            }
            Text(
                "옮길 칸을 탭하세요",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/** 위젯 모서리 = 화면 모서리 반지름 - 바깥 여백 (동심원 규칙) */
@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("M월 d일 EEEE", Locale.getDefault()).format(now) }
    val radius = (LocalScreenCorner.current - 16.dp).coerceAtLeast(20.dp)
    Box(modifier.glass(ContinuousCornerShape(radius)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(date, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
    }
}
