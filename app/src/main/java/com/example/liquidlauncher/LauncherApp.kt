package com.example.liquidlauncher

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.view.RoundedCorner
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 루트 화면.
 *  - HorizontalPager: 0 = 홈(4x7), 1 = 앱서랍 → 홈에서 왼쪽으로 넘기면 앱서랍
 *  - 앱 실행: 아이콘 위치에서 앱 창이 확대(시스템 스케일업) + 홈은 그 점을 중심으로 살짝 확대·페이드
 *  - 설정한 시간 동안 터치가 없으면 대기 화면(AOD 모드, 갤럭시 S24 이상에서만 켤 수 있음)
 *  - 런처 자체 잠금화면은 없다 (시스템 잠금을 그대로 사용)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherApp(
    st: LauncherState,
    onPickWallpaper: () -> Unit,
    onSetDefault: () -> Unit,
    isDefaultHome: () -> Boolean,
    onPickIcon: (AppInfo) -> Unit,
    onImportIcons: () -> Unit,
    onAddWidget: (AppWidgetProviderInfo) -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val ctx = LocalContext.current
        val view = LocalView.current
        val density = LocalDensity.current
        val activity = remember(ctx) { ctx.findActivity() }
        val pager = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()
        val zoom = remember { Animatable(0f) }
        var zoomOrigin by remember { mutableStateOf(TransformOrigin.Center) }
        var screenCorner by remember { mutableStateOf(40.dp) }
        var showMenu by remember { mutableStateOf(false) }
        var showAodMenu by remember { mutableStateOf(false) }
        var showWidgetPicker by remember { mutableStateOf(false) }
        var askDefault by remember { mutableStateOf(!st.store.askedDefault && !isDefaultHome()) }
        val touch = remember { longArrayOf(System.nanoTime()) }

        // 실제 화면 모서리 반지름 측정 (Android 12+, 창이 붙은 뒤에 값이 들어옴)
        LaunchedEffect(view) {
            if (Build.VERSION.SDK_INT >= 31) {
                repeat(20) {
                    val rc = view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                    if (rc != null) {
                        screenCorner = with(density) { rc.radius.toDp() }
                        return@LaunchedEffect
                    }
                    delay(100)
                }
            }
        }

        // 다른 앱에서 돌아오면 줌 복원 (스프링)
        LaunchedEffect(st.resumeTick) {
            zoom.animateTo(0f, iosSpring(0.5f, 0.85f))
        }
        // 홈 버튼을 다시 누르면 홈 페이지로
        LaunchedEffect(st.homeTick) {
            if (st.homeTick > 0) pager.animateScrollToPage(0)
        }
        BackHandler {
            if (pager.currentPage == 1) scope.launch { pager.animateScrollToPage(0) }
        }

        // ── AOD 모드: 유휴 감지 (설정한 시간 동안 터치 없음) ──
        LaunchedEffect(st.resumed, st.aodActive, st.aodEnabled, st.aodIdleSec, showMenu, showAodMenu, showWidgetPicker, askDefault) {
            if (!st.resumed || st.aodActive || !st.aodEnabled || showMenu || showAodMenu || showWidgetPicker || askDefault) {
                return@LaunchedEffect
            }
            touch[0] = System.nanoTime()
            while (true) {
                delay(1000)
                val idleMs = (System.nanoTime() - touch[0]) / 1_000_000L
                if (idleMs >= st.aodIdleSec * 1000L && (!st.aodOnlyCharging || st.isCharging())) {
                    st.enterAod()
                    break
                }
            }
        }
        // ── AOD 모드: 창 밝기/시스템 바/화면 유지. 최대 유지 시간이 지나면 화면 유지를 풀어 시스템이 끄게 한다 ──
        LaunchedEffect(st.aodActive) {
            val a = activity ?: return@LaunchedEffect
            if (st.aodActive) {
                applyAodWindow(a, on = true, keepOn = true)
                delay(st.aodMaxMin * 60_000L)
                applyAodWindow(a, on = true, keepOn = false)
            } else {
                applyAodWindow(a, on = false, keepOn = false)
            }
        }

        val launchApp: (AppInfo, Rect) -> Unit = { app, r ->
            val w = view.width.toFloat().coerceAtLeast(1f)
            val h = view.height.toFloat().coerceAtLeast(1f)
            zoomOrigin = TransformOrigin(r.center.x / w, r.center.y / h)
            scope.launch {
                val b = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                if (st.repo.launch(app, view, b)) {
                    zoom.animateTo(1f, iosSpring(0.45f, 0.9f))
                } else {
                    zoom.snapTo(0f)
                }
            }
        }

        CompositionLocalProvider(LocalScreenCorner provides screenCorner) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                touch[0] = System.nanoTime()
                            }
                        }
                    }
            ) {
                Backdrop(st.wallpaper, animated = !st.aodActive)

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val z = zoom.value
                        transformOrigin = zoomOrigin
                        scaleX = 1f + 0.12f * z
                        scaleY = 1f + 0.12f * z
                        alpha = 1f - 0.6f * z
                    }
                ) { page ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val o = abs((pager.currentPage - page) + pager.currentPageOffsetFraction)
                                    .coerceIn(0f, 1f)
                                val s = 1f - 0.12f * o
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.7f * o
                            }
                    ) {
                        if (page == 0) {
                            HomeScreen(
                                st,
                                onLaunch = launchApp,
                                onPickIcon = onPickIcon,
                                onLongPressEmpty = { showMenu = true }
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))) {
                                DrawerScreen(st, onLaunch = launchApp, onPickIcon = onPickIcon)
                            }
                        }
                    }
                }

                // 대기 화면 (아무 곳이나 터치하면 해제)
                AnimatedVisibility(
                    visible = st.aodActive,
                    enter = fadeIn(tween(700)),
                    exit = fadeOut(tween(300))
                ) {
                    AodScreen(st) { st.exitAod() }
                }
            }
        }

        if (showMenu) {
            AlertDialog(
                onDismissRequest = { showMenu = false },
                confirmButton = {},
                title = { Text("홈 화면") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TextButton(onClick = { showMenu = false; onPickWallpaper() }) { Text("배경화면 선택") }
                        TextButton(onClick = { showMenu = false; st.clearWallpaper() }) { Text("배경화면 초기화") }
                        TextButton(onClick = { showMenu = false; showWidgetPicker = true }) { Text("위젯 추가") }
                        TextButton(onClick = { showMenu = false; onImportIcons() }) { Text("아이콘 팩 가져오기 (여러 장)") }
                        TextButton(onClick = { showMenu = false; st.clearAllCustomIcons() }) { Text("아이콘 모두 기본으로") }
                        TextButton(onClick = {
                            showMenu = false
                            if (!st.addClock()) {
                                Toast.makeText(ctx, "시계 위젯(4×2)을 둘 빈 공간이 없어요", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("시계 위젯 추가 (기본 제공)") }
                        TextButton(onClick = { showMenu = false; showAodMenu = true }) { Text("AOD(대기 화면) 모드") }
                        TextButton(onClick = { showMenu = false; onSetDefault() }) { Text("기본 홈 앱으로 지정") }
                    }
                }
            )
        }

        if (showAodMenu) AodSettingsDialog(st) { showAodMenu = false }

        if (showWidgetPicker) {
            WidgetPickerDialog(
                st,
                onPick = { info -> showWidgetPicker = false; onAddWidget(info) },
                onDismiss = { showWidgetPicker = false }
            )
        }

        if (askDefault) {
            AlertDialog(
                onDismissRequest = { askDefault = false; st.store.askedDefault = true },
                title = { Text("기본 홈 앱으로 지정할까요?") },
                text = { Text("지정하면 홈 버튼을 눌렀을 때 이 런처가 열려요.") },
                confirmButton = {
                    TextButton(onClick = {
                        askDefault = false
                        st.store.askedDefault = true
                        onSetDefault()
                    }) { Text("지정") }
                },
                dismissButton = {
                    TextButton(onClick = { askDefault = false; st.store.askedDefault = true }) { Text("나중에") }
                }
            )
        }
    }
}
