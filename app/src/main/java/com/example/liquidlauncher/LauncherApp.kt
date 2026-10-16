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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private enum class Sheet { NONE, MENU, AOD, AOD_WARN, WIDGETS, DEFAULT_HOME }

/**
 * 루트 화면 (Edel OS Launcher).
 *  - BackdropProvider: 흐린 배경화면을 만들어 두고, 모든 유리 판이 같은 위치의 배경을 비춰 보이게 한다
 *  - HorizontalPager: 0 = 홈(4x7 + 독), 1 = 앱서랍 → 홈에서 왼쪽으로 넘기면 앱서랍
 *  - 앱 실행: 아이콘 위치에서 앱 창이 확대(시스템 스케일업) + 홈은 그 점을 중심으로 확대·페이드
 *  - 설정한 시간 동안 터치가 없으면 대기 화면(AOD 모드, 갤럭시 S24 이상에서만 켤 수 있음)
 *  - 메뉴/설정은 아래에서 올라오는 유리 시트 (Material 다이얼로그를 쓰지 않는다)
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
        var sheet by remember {
            mutableStateOf(if (!st.store.askedDefault && !isDefaultHome()) Sheet.DEFAULT_HOME else Sheet.NONE)
        }
        val touch = remember { longArrayOf(System.nanoTime()) }

        // 0(홈) ~ 1(앱서랍) 사이의 페이지 진행도
        val pageProgress: () -> Float = {
            (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
        }

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
        LaunchedEffect(st.resumed, st.aodActive, st.aodEnabled, st.aodIdleSec, sheet) {
            if (!st.resumed || st.aodActive || !st.aodEnabled || sheet != Sheet.NONE) {
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
            BackdropProvider(st.wallpaper, animate = !st.aodActive) {
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
                                    pageOffset = pageProgress,
                                    onLaunch = launchApp,
                                    onPickIcon = onPickIcon,
                                    onLongPressEmpty = { sheet = Sheet.MENU }
                                )
                            } else {
                                DrawerScreen(
                                    st,
                                    pageOffset = pageProgress,
                                    onLaunch = launchApp,
                                    onPickIcon = onPickIcon
                                )
                            }
                        }
                    }

                    // 페이지 점 (현재 페이지가 부드럽게 커진다)
                    PageDots(
                        count = 2,
                        progress = pageProgress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp)
                    )

                    // ───────── 유리 시트들 ─────────
                    GlassSheet(
                        visible = sheet == Sheet.MENU,
                        onDismiss = { sheet = Sheet.NONE },
                        title = "Edel OS"
                    ) {
                        SheetRow("배경화면 선택", "갤러리에서 이미지를 골라요") {
                            sheet = Sheet.NONE
                            onPickWallpaper()
                        }
                        SheetRow("배경화면 초기화") {
                            sheet = Sheet.NONE
                            st.clearWallpaper()
                        }
                        SheetRow("위젯 추가", "설치된 위젯에서 고르기") { sheet = Sheet.WIDGETS }
                        SheetRow("시계 위젯 추가", "기본 제공 · 4×2칸") {
                            sheet = Sheet.NONE
                            if (!st.addClock()) {
                                Toast.makeText(ctx, "시계 위젯(4×2)을 둘 빈 공간이 없어요", Toast.LENGTH_SHORT).show()
                            }
                        }
                        SheetRow("아이콘 팩 가져오기", "파일 이름이 앱 이름과 같으면 자동 적용") {
                            sheet = Sheet.NONE
                            onImportIcons()
                        }
                        SheetRow("아이콘 모두 기본으로", destructive = true) {
                            sheet = Sheet.NONE
                            st.clearAllCustomIcons()
                        }
                        SheetRow("AOD(대기 화면) 모드", "S24 시리즈 이상에서만 사용 가능") { sheet = Sheet.AOD }
                        SheetRow("기본 홈 앱으로 지정") {
                            sheet = Sheet.NONE
                            onSetDefault()
                        }
                    }

                    GlassSheet(
                        visible = sheet == Sheet.AOD,
                        onDismiss = { sheet = Sheet.NONE },
                        title = "AOD(대기 화면) 모드"
                    ) {
                        AodSettingsContent(st, onAskEnable = { sheet = Sheet.AOD_WARN })
                    }

                    GlassSheet(
                        visible = sheet == Sheet.AOD_WARN,
                        onDismiss = { sheet = Sheet.AOD },
                        title = "번인 위험 안내"
                    ) {
                        AodWarningContent(
                            onConfirm = {
                                st.changeAodEnabled(true)
                                sheet = Sheet.AOD
                            },
                            onCancel = { sheet = Sheet.AOD }
                        )
                    }

                    GlassSheet(
                        visible = sheet == Sheet.WIDGETS,
                        onDismiss = { sheet = Sheet.NONE },
                        title = "위젯 추가",
                        scrollable = false
                    ) {
                        WidgetPickerContent(st) { info ->
                            sheet = Sheet.NONE
                            onAddWidget(info)
                        }
                    }

                    GlassSheet(
                        visible = sheet == Sheet.DEFAULT_HOME,
                        onDismiss = {
                            st.store.askedDefault = true
                            sheet = Sheet.NONE
                        },
                        title = "기본 홈 앱으로 지정할까요?"
                    ) {
                        SheetNote("지정하면 홈 버튼을 눌렀을 때 Edel OS Launcher가 열려요.")
                        SheetRow("지정") {
                            st.store.askedDefault = true
                            sheet = Sheet.NONE
                            onSetDefault()
                        }
                        SheetRow("나중에") {
                            st.store.askedDefault = true
                            sheet = Sheet.NONE
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
        }
    }
}
