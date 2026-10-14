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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * 루트 화면 (Edel OS).
 *  - HorizontalPager: 0 = 홈(4x7), 1 = 앱서랍
 *    넘길 때: 배경 시차 이동 + 서랍 쪽으로 갈수록 배경이 흐려지고 어두워짐 + 페이지 3D 기울기·축소·페이드
 *  - 앱 실행: 아이콘 위치에서 앱 창이 확대(시스템 스케일업) + 홈은 그 점을 중심으로 살짝 확대·페이드
 *  - 길게 누르기 메뉴는 아래에서 올라오는 유리 시트
 *  - 프로세스 시작 시 Edel 인트로 한 번
 *  - 설정한 시간 동안 터치가 없으면 대기 화면(AOD 모드, 갤럭시 S24 이상에서만 켤 수 있음)
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
    MaterialTheme(
        colorScheme = EdelColorScheme,
        shapes = Shapes(extraLarge = RoundedCornerShape(30.dp))
    ) {
        val ctx = LocalContext.current
        val view = LocalView.current
        val dens = LocalDensity.current
        val activity = remember(ctx) { ctx.findActivity() }
        val pager = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()
        val zoom = remember { Animatable(0f) }
        var zoomOrigin by remember { mutableStateOf(TransformOrigin.Center) }
        var screenCorner by remember { mutableStateOf(40.dp) }
        var sheet by remember { mutableStateOf<SheetSpec?>(null) }
        val sheetHolder = remember { arrayOfNulls<SheetSpec>(1) } // 닫히는 애니메이션 동안 내용을 유지
        if (sheet != null) sheetHolder[0] = sheet
        val sheetOpen = sheet != null
        var showAodMenu by remember { mutableStateOf(false) }
        var showWidgetPicker by remember { mutableStateOf(false) }
        var askDefault by remember { mutableStateOf(!st.store.askedDefault && !isDefaultHome()) }
        var introDone by remember { mutableStateOf(st.introPlayed) }
        val touch = remember { longArrayOf(System.nanoTime()) }

        // 0 = 홈, 1 = 서랍 (넘기는 중에는 소수)
        val pos = { pager.currentPage + pager.currentPageOffsetFraction }

        // 실제 화면 모서리 반지름 측정 (Android 12+, 창이 붙은 뒤에 값이 들어옴)
        LaunchedEffect(view) {
            if (Build.VERSION.SDK_INT >= 31) {
                repeat(20) {
                    val rc = view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                    if (rc != null) {
                        screenCorner = with(dens) { rc.radius.toDp() }
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
        LaunchedEffect(st.resumed, st.aodActive, st.aodEnabled, st.aodIdleSec, sheetOpen, showAodMenu, showWidgetPicker, askDefault) {
            if (!st.resumed || st.aodActive || !st.aodEnabled || sheetOpen || showAodMenu || showWidgetPicker || askDefault) {
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

        fun homeMenu() {
            sheet = SheetSpec(
                title = "Edel OS",
                subtitle = "홈 화면 설정",
                actions = listOf(
                    SheetAction("배경화면 선택", "🖼") { onPickWallpaper() },
                    SheetAction("배경화면 초기화", "🌌") { st.clearWallpaper() },
                    SheetAction("위젯 추가", "🧩") { showWidgetPicker = true },
                    SheetAction("시계 위젯 추가", "🕐") {
                        if (!st.addClock()) {
                            Toast.makeText(ctx, "시계 위젯(4×2)을 둘 빈 공간이 없어요", Toast.LENGTH_SHORT).show()
                        }
                    },
                    SheetAction("아이콘 팩 가져오기 (여러 장)", "🎨") { onImportIcons() },
                    SheetAction("아이콘 모두 기본으로", "🔄") { st.clearAllCustomIcons() },
                    SheetAction("AOD(대기 화면) 모드", "🌙") { showAodMenu = true },
                    SheetAction("기본 홈 앱으로 지정", "🏠") { onSetDefault() }
                )
            )
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
                Backdrop(
                    st.wallpaper,
                    animated = !st.aodActive,
                    parallax = { pos().coerceIn(0f, 1f) },
                    blurPx = { pos().coerceIn(0f, 1f) * 20f }
                )
                // 서랍으로 갈수록 어두워지는 막
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = pos().coerceIn(0f, 1f) }
                        .background(Color.Black.copy(alpha = 0.34f))
                )

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
                                // o: 이 페이지가 가운데에서 얼마나 떨어졌는지 (왼쪽 +, 오른쪽 -)
                                val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                val a = abs(o).coerceIn(0f, 1f)
                                val s = 1f - 0.10f * a
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.75f * a
                                cameraDistance = 14f * density
                                rotationY = -o * 7f
                            }
                    ) {
                        if (page == 0) {
                            HomeScreen(
                                st,
                                enterKey = st.homeTick * 2 + (if (introDone) 1 else 0),
                                onLaunch = launchApp,
                                onPickIcon = onPickIcon,
                                onLongPressEmpty = { homeMenu() },
                                onShowSheet = { sheet = it }
                            )
                        } else {
                            DrawerScreen(
                                st,
                                onLaunch = launchApp,
                                onPickIcon = onPickIcon,
                                onShowSheet = { sheet = it }
                            )
                        }
                    }
                }

                // 페이지 표시 (늘어나는 알약)
                PageDots(
                    count = 2,
                    position = pos,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 5.dp)
                )

                // 대기 화면 (아무 곳이나 터치하면 해제)
                AnimatedVisibility(
                    visible = st.aodActive,
                    enter = fadeIn(tween(700)),
                    exit = fadeOut(tween(300))
                ) {
                    AodScreen(st) { st.exitAod() }
                }

                // 길게 누르기 메뉴 시트
                EdelSheet(visible = sheetOpen, spec = sheetHolder[0], onDismiss = { sheet = null })

                // Edel 인트로 (프로세스 시작 시 한 번)
                if (!introDone) {
                    EdelIntro {
                        st.introPlayed = true
                        introDone = true
                    }
                }
            }
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
                title = { Text("Edel OS를 기본 홈 앱으로 지정할까요?") },
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
