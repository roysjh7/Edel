package com.example.liquidlauncher

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerScreen(
    st: LauncherState,
    onLaunch: (AppInfo, Rect) -> Unit,
    onPickIcon: (AppInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val filtered = remember(st.apps, query) {
        if (query.isBlank()) st.apps else st.apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    // 4열 기준 칸 너비 → 홈과 같은 비율(64%)로 아이콘 크기 결정
    val cw = (LocalConfiguration.current.screenWidthDp.dp - 24.dp) / 4
    val iconSize = cw * 0.64f

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .glass(ContinuousCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("앱 검색", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filtered, key = { it.key }) { app ->
                Box {
                    AppCell(
                        app, st,
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        iconSize = iconSize,
                        onClick = { r -> onLaunch(app, r) },
                        onLongClick = { menuFor = app.key }
                    )
                    DropdownMenu(expanded = menuFor == app.key, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text("홈에 추가") },
                            onClick = {
                                if (!st.addApp(app)) {
                                    Toast.makeText(ctx, "홈 화면에 빈 칸이 없어요", Toast.LENGTH_SHORT).show()
                                }
                                menuFor = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("아이콘 변경") },
                            onClick = { onPickIcon(app); menuFor = null }
                        )
                        DropdownMenuItem(
                            text = { Text("기본 아이콘으로") },
                            onClick = { st.clearCustomIcon(app); menuFor = null }
                        )
                        DropdownMenuItem(
                            text = { Text("앱 정보") },
                            onClick = { st.openAppInfo(app); menuFor = null }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = { st.uninstall(app); menuFor = null }
                        )
                    }
                }
            }
        }
    }
}
