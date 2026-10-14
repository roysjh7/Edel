package com.example.liquidlauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.ceil
import kotlin.math.max

private const val HOST_ID = 1024

/**
 * 안드로이드 위젯 호스트.
 *  - 위젯 목록 조회 → 바인딩(필요하면 시스템이 사용자 허용을 물음) → (설정 화면이 있으면) 설정 → 홈 그리드에 배치
 *  - 배치 정보(HomeItem.type="widget", payload=appWidgetId)는 LauncherStore 에 저장된다
 */
class WidgetController(private val ctx: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(ctx)
    val host = AppWidgetHost(ctx, HOST_ID)

    fun startListening() {
        try {
            host.startListening()
        } catch (e: Exception) {
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
    }

    fun delete(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun appLabel(info: AppWidgetProviderInfo): String = try {
        val pm = ctx.packageManager
        pm.getApplicationInfo(info.provider.packageName, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        info.provider.packageName
    }

    fun widgetLabel(info: AppWidgetProviderInfo): String =
        try { info.loadLabel(ctx.packageManager) } catch (e: Exception) { info.provider.className }

    /** 설치된 위젯 목록 (앱 이름 → 위젯 이름 순) */
    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders
            .filter { it.provider != null }
            .sortedWith(compareBy({ appLabel(it).lowercase() }, { widgetLabel(it).lowercase() }))

    /** 위젯이 차지할 (가로 칸, 세로 칸). 위젯이 권장 칸 수를 주면 그걸, 아니면 최소 크기로 계산 */
    fun spansFor(info: AppWidgetProviderInfo, cellWdp: Float, cellHdp: Float): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, COLS) to info.targetCellHeight.coerceIn(1, ROWS)
        }
        val d = ctx.resources.displayMetrics.density
        val c = ceil((info.minWidth / d) / max(cellWdp, 1f)).toInt().coerceIn(1, COLS)
        val r = ceil((info.minHeight / d) / max(cellHdp, 1f)).toInt().coerceIn(1, ROWS)
        return c to r
    }
}

/** 눌러서 길게 → 콜백. 이벤트를 소비하지 않아서 위젯 자체 터치/스크롤은 그대로 동작한다. */
private suspend fun PointerInputScope.detectLongPressPassive(onLongPress: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var cancelled = false
        val completed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id }
                if (c == null || c.changedToUp() || ev.changes.size > 1 ||
                    (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                ) {
                    cancelled = true
                    break
                }
            }
        }
        if (completed == null && !cancelled) onLongPress()
    }
}

@Suppress("DEPRECATION")
@Composable
fun WidgetView(
    st: LauncherState,
    item: HomeItem,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit
) {
    val id = item.payload.toIntOrNull()
    val info = remember(id) { id?.let { st.widgets.manager.getAppWidgetInfo(it) } }
    if (id == null || info == null) {
        // 위젯 앱이 삭제되는 등 정보를 못 읽으면 홈에서 치운다
        LaunchedEffect(item.id) { st.remove(item.id) }
    } else {
        val lp by rememberUpdatedState(onLongPress)
        val radius = maxOf(LocalScreenCorner.current - 16.dp, 20.dp)
        val wDp = (width.value - 8f).toInt().coerceAtLeast(1)
        val hDp = (height.value - 8f).toInt().coerceAtLeast(1)
        Box(
            modifier
                .padding(4.dp)
                .clip(ContinuousCornerShape(radius))
                .pointerInput(item.id) { detectLongPressPassive { lp() } }
        ) {
            AndroidView(
                factory = { c -> st.widgets.host.createView(c, id, info) },
                modifier = Modifier.fillMaxSize(),
                update = { v -> v.updateAppWidgetSize(null, wDp, hDp, wDp, hDp) }
            )
        }
    }
}

private fun loadPreview(ctx: Context, info: AppWidgetProviderInfo): ImageBitmap? = try {
    val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
    if (d == null) {
        null
    } else {
        val iw = d.intrinsicWidth.coerceAtLeast(1)
        val ih = d.intrinsicHeight.coerceAtLeast(1)
        val scale = 320f / max(iw, ih).toFloat()
        d.toBitmap((iw * scale).toInt().coerceAtLeast(1), (ih * scale).toInt().coerceAtLeast(1)).asImageBitmap()
    }
} catch (e: Throwable) {
    null
}

@Composable
fun WidgetPickerDialog(
    st: LauncherState,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val list = remember { st.widgets.providers() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("위젯 추가") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        text = {
            if (list.isEmpty()) {
                Text("설치된 위젯이 없어요.")
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.provider.flattenToString() }) { info ->
                        val preview = remember(info) { loadPreview(ctx, info) }
                        val (c, r) = remember(info) { st.widgets.spansFor(info, st.cellWdp, st.cellHdp) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(ContinuousCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onPick(info) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                                if (preview != null) {
                                    Image(
                                        bitmap = preview,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(st.widgets.widgetLabel(info), fontSize = 15.sp, color = Color.White)
                                Text(
                                    "${st.widgets.appLabel(info)} · ${c}×${r}칸",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
