package com.example.liquidlauncher

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/** AOD 설정 시트의 내용 (스위치를 켜려 하면 onAskEnable 로 번인 위험 안내 시트를 먼저 띄운다) */
@Composable
fun ColumnScope.AodSettingsContent(st: LauncherState, onAskEnable: () -> Unit) {
    if (!st.aodSupported) {
        SheetNote(
            "이 기기(${DeviceSupport.modelName()})에서는 켤 수 없어요.\n" +
                "번인 위험 때문에 갤럭시 S24 시리즈 이상(LTPO 디스플레이)에서만 사용할 수 있어요.",
            Color(0xFFFF8A80)
        )
    }
    SheetSwitchRow("AOD 모드 사용", st.aodEnabled, enabled = st.aodSupported) { on ->
        if (on) onAskEnable() else st.changeAodEnabled(false)
    }
    SheetChips("대기 진입 시간", listOf(15, 30, 60), st.aodIdleSec, { "${it}초" }) {
        st.changeAodIdleSec(it)
    }
    SheetChips("최대 유지 시간", listOf(1, 3, 5, 10), st.aodMaxMin, { "${it}분" }) {
        st.changeAodMaxMin(it)
    }
    SheetSwitchRow("충전 중에만 사용", st.aodOnlyCharging) { st.changeAodOnlyCharging(it) }
    SheetNote(
        "시스템 AOD가 아니라, 런처가 화면을 켜 둔 채 어둡게 보여주는 대기 화면이에요. " +
            "대기 진입 시간은 폰의 '화면 꺼짐 시간'보다 짧아야 동작해요."
    )
}

@Composable
fun ColumnScope.AodWarningContent(onConfirm: () -> Unit, onCancel: () -> Unit) {
    SheetNote(
        "OLED 화면에 같은 이미지를 오래 켜 두면 잔상(번인)이 생길 수 있어요.\n\n" +
            "• 갤럭시 S24 시리즈 이상(LTPO)에서만 켤 수 있어요.\n" +
            "• 1분마다 화면을 조금씩 이동하고, 최대 유지 시간이 지나면 자동으로 꺼져요.\n" +
            "• 그래도 위험이 0은 아니에요. 오래 켜 둘 땐 쓰지 마세요.\n" +
            "• 이 모드 동안 폰은 잠금 해제된 상태라, 옆에서 화면을 터치하면 홈 화면에 접근할 수 있어요.",
        Color.White.copy(alpha = 0.85f)
    )
    SheetRow("이해했고 켤게요") { onConfirm() }
    SheetRow("취소") { onCancel() }
}
