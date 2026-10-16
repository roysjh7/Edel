package com.example.liquidlauncher

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun SearchGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = Color.White.copy(alpha = 0.8f)
        drawCircle(c, radius = s * 0.32f, center = Offset(s * 0.42f, s * 0.42f), style = Stroke(width = s * 0.12f))
        drawLine(
            c, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.92f, s * 0.92f),
            strokeWidth = s * 0.12f, cap = StrokeCap.Round
        )
    }
}

/**
 * 앱서랍: 흐린 배경화면 위에 유리 검색창 + 아이콘 격자.
 * 열릴 때 아이콘이 차례로 튀어나오고, 페이지를 넘기는 동안 열마다 벌어지는 웨이브가 생긴다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerScreen(
    st: LauncherState,
    pageOffset: () -> Float,
    onLaunch: (AppInfo, Rect) -> Unit,
    onPickIcon: (AppInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val filtered = remember(st.apps, query) {
        if (query.isBlank()) st.apps else st.apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val searchScale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = iosSpring(0.4f, 0.7f),
        label = "search"
    )

    Column(
        Modifier
            .fillMaxSize()
            .backdropBlurFill(0.32f)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = searchScale; scaleY = searchScale }
                .liquidGlass(ContinuousCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchGlyph(Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("앱 검색", color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(filtered, key = { _, a -> a.key }) { index, app ->
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay((index % 16) * 24L)
                    shown = true
                }
                val s by animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = iosSpring(0.45f, 0.62f),
                    label = "in"
                )
                Box(
                    Modifier.graphicsLayer {
                        // 등장 + 페이지를 넘기는 동안 열마다 벌어지는 웨이브
                        val k = 1f - pageOffset()
                        val sc = 0.7f + 0.3f * s
                        scaleX = sc
                        scaleY = sc
                        alpha = s
                        translationY = (1f - s) * 30f
                        translationX = k * ((index % 4) - 1.5f) * 40f
                    }
                ) {
                    AppCell(
                        app, st,
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        onClick = { r -> onLaunch(app, r) },
                        onLongClick = { menuFor = app.key }
                    )
                    GlassMenu(
                        expanded = menuFor == app.key,
                        onDismiss = { menuFor = null },
                        actions = listOf(
                            MenuAction("홈에 추가") {
                                if (!st.addApp(app)) {
                                    Toast.makeText(ctx, "홈 화면에 빈 칸이 없어요", Toast.LENGTH_SHORT).show()
                                }
                                menuFor = null
                            },
                            MenuAction("아이콘 변경") { onPickIcon(app); menuFor = null },
                            MenuAction("기본 아이콘으로") { st.clearCustomIcon(app); menuFor = null },
                            MenuAction("앱 정보") { st.openAppInfo(app); menuFor = null },
                            MenuAction("삭제", destructive = true) { st.uninstall(app); menuFor = null }
                        )
                    )
                }
            }
        }
    }
}
