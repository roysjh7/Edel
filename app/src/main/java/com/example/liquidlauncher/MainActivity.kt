package com.example.liquidlauncher

import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val st get() = (application as LauncherApplication).state

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) st.setWallpaper(uri)
        }
    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    /** 앱 하나의 아이콘을 갤러리 이미지로 교체 */
    private val pickIcon =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val target = st.iconTarget
            st.iconTarget = null
            if (uri != null && target != null) {
                val ok = st.setCustomIcon(target, uri)
                toast(if (ok) "${target.label} 아이콘을 바꿨어요" else "이미지를 읽지 못했어요")
            }
        }

    /** 여러 장 한 번에: 파일 이름이 패키지명/앱 이름과 같으면 자동 적용 */
    private val importIcons =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val r = st.importIconPack(uris)
                val miss = if (r.unmatched.isEmpty()) "" else
                    "\n매칭 실패 ${r.unmatched.size}개: " + r.unmatched.take(3).joinToString(", ") +
                        if (r.unmatched.size > 3) " …" else ""
                toast("${r.applied}개 아이콘을 적용했어요$miss")
            }
        }

    // 위젯 추가 진행 상태 (바인딩 허용 → 설정 화면 → 배치)
    private var pendingWidgetId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        // 120Hz 가변 주사율 디스플레이에서 최대 주사율을 선호 (부드러운 애니메이션)
        window.attributes = window.attributes.also { it.preferredRefreshRate = 120f }

        setContent {
            LauncherApp(
                st = st,
                onPickWallpaper = { pickImage.launch("image/*") },
                onSetDefault = { requestHomeRole() },
                isDefaultHome = { isDefaultHome() },
                onPickIcon = { app ->
                    st.iconTarget = app
                    pickIcon.launch("image/*")
                },
                onImportIcons = { importIcons.launch(arrayOf("image/*")) },
                onAddWidget = { info -> beginAddWidget(info) }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        st.widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        st.widgets.stopListening()
    }

    override fun onResume() {
        super.onResume()
        st.resumed = true
        st.resumeTick++
    }

    override fun onPause() {
        super.onPause()
        st.resumed = false
        // 다른 앱으로 가거나 화면이 꺼지면 대기 화면을 해제하고 창 설정을 원복
        st.exitAod()
        applyAodWindow(this, on = false, keepOn = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            st.homeTick++
        }
    }

    // ───────── 위젯 추가 ─────────
    @Suppress("DEPRECATION")
    private fun beginAddWidget(info: AppWidgetProviderInfo) {
        val w = st.widgets
        val id = w.host.allocateAppWidgetId()
        pendingWidgetId = id
        pendingInfo = info
        if (w.manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            onWidgetBound()
        } else {
            // 이 런처가 아직 허용받지 못한 위젯 → 시스템이 사용자에게 허용을 묻는다
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                cancelPendingWidget()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun onWidgetBound() {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            try {
                st.widgets.host.startAppWidgetConfigureActivityForResult(
                    this, pendingWidgetId, 0, REQ_CONFIG, null
                )
            } catch (e: Exception) {
                finishAddWidget() // 설정 화면을 못 열면 기본값으로 배치
            }
        } else {
            finishAddWidget()
        }
    }

    private fun finishAddWidget() {
        val info = pendingInfo
        val id = pendingWidgetId
        pendingWidgetId = -1
        pendingInfo = null
        if (info != null && id >= 0 && !st.addWidget(id, info)) {
            st.widgets.delete(id)
            toast("위젯을 둘 빈 공간이 없어요")
        }
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId >= 0) st.widgets.delete(pendingWidgetId)
        pendingWidgetId = -1
        pendingInfo = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) onWidgetBound() else cancelPendingWidget()
            REQ_CONFIG -> if (resultCode == RESULT_OK) finishAddWidget() else cancelPendingWidget()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    companion object {
        private const val REQ_BIND = 4101
        private const val REQ_CONFIG = 4102
    }
}
