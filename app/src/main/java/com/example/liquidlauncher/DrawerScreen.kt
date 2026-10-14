package com.example.liquidlauncher

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerScreen(
    st: LauncherState,
    onLaunch: (AppInfo, Rect) -> Unit,
    onPickIcon: (AppInfo) -> Unit,
    onShowSheet: (SheetSpec) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val filtered = remember(st.apps, query) {
        if (query.isBlank()) st.apps else st.apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    // 4열 기준 칸 너비 → 홈과 같은 비율(64%)로 아이콘 크기 결정
    val cw = (LocalConfiguration.current.screenWidthDp.dp - 24.dp) / 4
    val iconSize = cw * 0.64f

    val searchSrc = remember { MutableInteractionSource() }
    val focused by searchSrc.collectIsFocusedAsState()
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(260), label = "searchGlow")
    val searchShape = remember { ContinuousCornerShape(18.dp) }

    fun sheetFor(app: AppInfo) {
        onShowSheet(
            SheetSpec(
                title = app.label,
                subtitle = "앱 서랍",
                icon = st.repo.icon(app),
                actions = listOf(
                    SheetAction("홈에 추가", "➕") {
                        if (!st.addApp(app)) {
                            Toast.makeText(ctx, "홈 화면에 빈 칸이 없어요", Toast.LENGTH_SHORT).show()
                        }
                    },
                    SheetAction("아이콘 변경", "🎨") { onPickIcon(app) },
                    SheetAction("기본 아이콘으로", "🔄") { st.clearCustomIcon(app) },
                    SheetAction("앱 정보", "ℹ️") { st.openAppInfo(app) },
                    SheetAction("삭제", "🗑", destructive = true) { st.uninstall(app) }
                )
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 22.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            interactionSource = searchSrc,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Edel.Cyan),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 1f + 0.012f * glow
                    scaleX = s
                    scaleY = s
                }
                .glass(searchShape, 1f + 0.5f * glow)
                .border(1.5.dp, Edel.Violet.copy(alpha = 0.9f * glow), searchShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 15.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("앱 검색", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                        }
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        Text(
                            "✕",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 15.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { query = "" }
                        )
                    }
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(filtered, key = { _, app -> app.key }) { i, app ->
                    AppCell(
                        app, st,
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        iconSize = iconSize,
                        // 처음 보이는 화면만 물결처럼 등장 (스크롤로 나오는 아이콘은 바로 나타남)
                        index = if (i < 24) i else 0,
                        onClick = { r -> onLaunch(app, r) },
                        onLongClick = { sheetFor(app) }
                    )
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    "검색 결과가 없어요",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
