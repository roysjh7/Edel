package com.example.liquidlauncher

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 기기 지원 여부. AOD(대기 화면) 모드는 번인 위험 때문에
 * 갤럭시 S24 시리즈 이상(모델번호 SM-S921 이상, LTPO 디스플레이)에서만 켤 수 있다.
 * 예) S22 = SM-S901/906/908, S23 = SM-S911/916/918 → 차단 / S24 = SM-S921/926/928, S25 = SM-S931... → 허용
 * (S24 FE = SM-S721 같은 FE 모델은 번호가 낮아 차단된다.)
 */
object DeviceSupport {
    const val MIN_MODEL_NUMBER = 921
    private val modelRegex = Regex("^SM-S(\\d{3})")

    fun galaxySNumber(): Int? =
        modelRegex.find(Build.MODEL.uppercase())?.groupValues?.get(1)?.toIntOrNull()

    fun isAodSupported(): Boolean = (galaxySNumber() ?: 0) >= MIN_MODEL_NUMBER

    fun modelName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}

@Composable
fun AodSettingsDialog(st: LauncherState, onDismiss: () -> Unit) {
    var showWarning by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AOD(대기 화면) 모드") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!st.aodSupported) {
                    Text(
                        "이 기기(${DeviceSupport.modelName()})에서는 켤 수 없어요.\n" +
                            "번인 위험 때문에 갤럭시 S24 시리즈 이상(LTPO 디스플레이)에서만 사용할 수 있어요.",
                        color = Color(0xFFFFB4AB),
                        fontSize = 13.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AOD 모드 사용", modifier = Modifier.weight(1f))
                    Switch(
                        checked = st.aodEnabled,
                        enabled = st.aodSupported,
                        onCheckedChange = { on ->
                            if (on) showWarning = true else st.setAodEnabled(false)
                        }
                    )
                }
                OptionRow("대기 진입 시간", listOf(15, 30, 60), st.aodIdleSec, { "${it}초" }) {
                    st.setAodIdleSec(it)
                }
                OptionRow("최대 유지 시간", listOf(1, 3, 5, 10), st.aodMaxMin, { "${it}분" }) {
                    st.setAodMaxMin(it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("충전 중에만 사용", modifier = Modifier.weight(1f))
                    Switch(
                        checked = st.aodOnlyCharging,
                        onCheckedChange = { st.setAodOnlyCharging(it) }
                    )
                }
                Text(
                    "시스템 AOD가 아니라, 런처가 화면을 켜 둔 채 어둡게 보여주는 대기 화면이에요. " +
                        "대기 진입 시간은 폰의 '화면 꺼짐 시간'보다 짧아야 동작해요.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    )

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("번인 위험 안내") },
            text = {
                Text(
                    "OLED 화면에 같은 이미지를 오래 켜 두면 잔상(번인)이 생길 수 있어요.\n\n" +
                        "• 갤럭시 S24 시리즈 이상(LTPO)에서만 켤 수 있어요.\n" +
                        "• 1분마다 화면을 조금씩 이동하고, 최대 유지 시간이 지나면 자동으로 꺼져요.\n" +
                        "• 그래도 위험이 0은 아니에요. 오래 켜 둘 땐 쓰지 마세요.\n" +
                        "• 이 모드 동안 폰은 잠금 해제된 상태라, 옆에서 화면을 터치하면 홈 화면에 접근할 수 있어요.\n\n" +
                        "그래도 켤까요?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    st.setAodEnabled(true)
                    showWarning = false
                }) { Text("이해했고 켤게요") }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<Int>,
    selected: Int,
    fmt: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            options.forEach { o ->
                val sel = o == selected
                Box(
                    Modifier
                        .clip(ContinuousCornerShape(10.dp))
                        .background(Color.White.copy(alpha = if (sel) 0.35f else 0.1f))
                        .clickable { onSelect(o) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(fmt(o), fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}
