package com.example.liquidlauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** 프로세스 단위로 상태를 들고 있어서, Activity 가 다시 만들어져도 설정/배치가 유지된다. */
class LauncherApplication : Application() {
    lateinit var state: LauncherState
        private set

    override fun onCreate() {
        super.onCreate()
        state = LauncherState(this)

        // 앱 설치/삭제/변경 시 목록 갱신
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    // 업데이트 중 일시적 제거는 무시 (홈 배치를 유지)
                    if (i.action == Intent.ACTION_PACKAGE_REMOVED &&
                        i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    ) return
                    state.refreshApps()
                }
            },
            pkgFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
