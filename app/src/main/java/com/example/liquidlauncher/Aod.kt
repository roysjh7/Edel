package com.example.liquidlauncher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 마지막 터치 후 이 시간이 지나면 대기 화면(AOD)으로 진입 */
const val AOD_IDLE_MS = 12_000L

/** AOD 중 창 밝기 (0.0 ~ 1.0). 너무 어두우면 올리세요. */
const val AOD_BRIGHTNESS = 0.06f

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 앱 창의 밝기를 낮추고, 시스템 바를 숨기고, (선택) 화면 꺼짐을 막는다. on=false 면 모두 원복. */
fun applyAodWindow(a: Activity, on: Boolean, keepOn: Boolean) {
    val w = a.window
    val lp = w.attributes
    lp.screenBrightness = if (on) AOD_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    lp.preferredRefreshRate = if (on) 0f else 120f // 대기 화면에선 LTPO가 주사율을 낮출 수 있게
    w.attributes = lp
    if (on && keepOn) {
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    val ctrl = WindowCompat.getInsetsController(w, w.decorView)
    if (on) {
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        ctrl.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** iOS 스타일 대기 화면: 배경화면을 전체화면으로 어둡게 + 날짜/시계. 번인 방지로 1분마다 살짝 이동. */
@Composable
fun AodScreen(st: LauncherState, onWake: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = (shift + 1) % 4
        }
    }
    val dx by animateDpAsState(listOf(0.dp, 6.dp, 0.dp, (-6).dp)[shift], tween(2500), label = "aodDx")
    val dy by animateDpAsState(listOf(0.dp, 4.dp, 8.dp, 4.dp)[shift], tween(2500), label = "aodDy")
    val timeText = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val dateText = remember(now) { SimpleDateFormat("M월 d일 EEEE", Locale.getDefault()).format(now) }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onPress = { onWake() })
        }
    ) {
        // 번인 방지: 배경화면도 시계와 함께 1분마다 조금씩 이동 (가장자리가 비지 않게 살짝 확대)
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.05f
                scaleY = 1.05f
                translationX = dx.toPx()
                translationY = dy.toPx()
            }
        ) {
            Backdrop(st.wallpaper, dim = 0.55f, animated = false)
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 120.dp).offset(dx, dy),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(dateText, color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
            Text(timeText, color = Color.White.copy(alpha = 0.9f), fontSize = 88.sp, fontWeight = FontWeight.Light)
        }
    }
}
