package com.example.liquidlauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID

const val COLS = 4
const val ROWS = 7

/** 런처 전체 상태. Application 에서 만들어 프로세스가 사는 동안 유지된다. */
class LauncherState(private val ctx: Context) {
    val repo = AppRepository(ctx)
    val store = LauncherStore(ctx)
    val widgets = WidgetController(ctx)

    var apps by mutableStateOf(repo.loadApps())
        private set
    val items = mutableStateListOf<HomeItem>().also { it.addAll(store.loadItems()) }
    var wallpaper by mutableStateOf(loadWallpaper())
        private set

    /** 홈 그리드 한 칸의 크기(dp). 위젯이 몇 칸을 차지할지 계산하는 데 쓴다 (HomeScreen 이 갱신) */
    var cellWdp: Float = 80f
    var cellHdp: Float = 100f

    /** 아이콘이 바뀌면 올라가는 값 (아이콘 표시를 다시 읽게 하는 신호) */
    var iconVersion by mutableIntStateOf(0)
        private set

    /** '아이콘 변경'으로 갤러리를 띄워 둔 동안 대상 앱 */
    var iconTarget: AppInfo? = null

    /** 홈 버튼 재입력 / onResume 신호 (애니메이션 트리거용) */
    var homeTick by mutableIntStateOf(0)
    var resumeTick by mutableIntStateOf(0)

    // ---------- AOD(대기 화면) 모드 ----------
    /** 갤럭시 S24 시리즈 이상(LTPO)인지. 아니면 켤 수 없다. */
    val aodSupported: Boolean = DeviceSupport.isAodSupported()
    var aodEnabled by mutableStateOf(aodSupported && store.aodEnabled)
        private set
    var aodIdleSec by mutableIntStateOf(store.aodIdleSec)
        private set
    var aodMaxMin by mutableIntStateOf(store.aodMaxMin)
        private set
    var aodOnlyCharging by mutableStateOf(store.aodOnlyCharging)
        private set
    var aodActive by mutableStateOf(false)
        private set

    /** 런처가 화면 맨 앞(resumed)인지 */
    var resumed by mutableStateOf(false)

    /** 켜기 요청. 지원하지 않는 기기면 false (켜지지 않음) */
    fun setAodEnabled(on: Boolean): Boolean {
        if (on && !aodSupported) return false
        aodEnabled = on
        store.aodEnabled = on
        if (!on) exitAod()
        return true
    }

    fun setAodIdleSec(v: Int) {
        aodIdleSec = v
        store.aodIdleSec = v
    }

    fun setAodMaxMin(v: Int) {
        aodMaxMin = v
        store.aodMaxMin = v
    }

    fun setAodOnlyCharging(v: Boolean) {
        aodOnlyCharging = v
        store.aodOnlyCharging = v
    }

    fun enterAod() {
        if (aodEnabled) aodActive = true
    }

    fun exitAod() {
        aodActive = false
    }

    fun isCharging(): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val s = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
    }

    // ---------- 앱 목록 ----------
    fun refreshApps() {
        repo.clearCache()
        apps = repo.loadApps()
        val keys = apps.map { it.key }.toSet()
        val gone = items.filter { it.type == "app" && it.payload !in keys }
        if (gone.isNotEmpty()) {
            items.removeAll(gone)
            save()
        }
    }

    fun openAppInfo(app: AppInfo) = startSafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
    )

    fun uninstall(app: AppInfo) = startSafely(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
    )

    private fun startSafely(i: Intent) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    // ---------- 홈 그리드 (4 x 7) ----------
    private fun save() = store.saveItems(items.toList())

    fun isFree(col: Int, row: Int, spanC: Int, spanR: Int, ignoreId: String? = null): Boolean {
        if (col < 0 || row < 0 || col + spanC > COLS || row + spanR > ROWS) return false
        return items.none {
            it.id != ignoreId &&
                col < it.col + it.spanC && it.col < col + spanC &&
                row < it.row + it.spanR && it.row < row + spanR
        }
    }

    private fun findFree(spanC: Int, spanR: Int): Pair<Int, Int>? {
        for (r in 0..(ROWS - spanR)) for (c in 0..(COLS - spanC)) {
            if (isFree(c, r, spanC, spanR)) return c to r
        }
        return null
    }

    fun addApp(app: AppInfo): Boolean {
        if (items.any { it.type == "app" && it.payload == app.key }) return true
        val pos = findFree(1, 1) ?: return false
        items.add(HomeItem(UUID.randomUUID().toString(), "app", pos.first, pos.second, 1, 1, app.key))
        save()
        return true
    }

    fun addClock(): Boolean {
        val pos = findFree(4, 2) ?: return false
        items.add(HomeItem(UUID.randomUUID().toString(), "clock", pos.first, pos.second, 4, 2, ""))
        save()
        return true
    }

    fun addWidget(id: Int, info: android.appwidget.AppWidgetProviderInfo): Boolean {
        val (c, r) = widgets.spansFor(info, cellWdp, cellHdp)
        val pos = findFree(c, r) ?: return false
        items.add(HomeItem(UUID.randomUUID().toString(), "widget", pos.first, pos.second, c, r, id.toString()))
        save()
        return true
    }

    fun remove(id: String) {
        items.firstOrNull { it.id == id && it.type == "widget" }
            ?.payload?.toIntOrNull()?.let { widgets.delete(it) }
        items.removeAll { it.id == id }
        save()
    }

    // ---------- 커스텀 아이콘 ----------
    fun setCustomIcon(app: AppInfo, uri: Uri): Boolean {
        val ok = repo.setCustomIcon(app, uri)
        if (ok) iconVersion++
        return ok
    }

    fun clearCustomIcon(app: AppInfo) {
        repo.clearCustomIcon(app)
        iconVersion++
    }

    fun clearAllCustomIcons() {
        repo.clearAllCustomIcons()
        iconVersion++
    }

    fun importIconPack(uris: List<Uri>): IconImportResult {
        val r = repo.importIconPack(uris, apps)
        if (r.applied > 0) iconVersion++
        return r
    }

    fun move(id: String, col: Int, row: Int) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return
        val me = items[idx]
        if (isFree(col, row, me.spanC, me.spanR, id)) {
            items[idx] = me.copy(col = col, row = row)
            save()
            return
        }
        if (me.spanC == 1 && me.spanR == 1) {
            val oi = items.indexOfFirst { o ->
                o.id != id && o.spanC == 1 && o.spanR == 1 && o.col == col && o.row == row
            }
            if (oi >= 0) {
                val other = items[oi]
                items[oi] = other.copy(col = me.col, row = me.row)
                items[idx] = me.copy(col = col, row = row)
                save()
            }
        }
    }

    // ---------- 배경화면 ----------
    private fun loadWallpaper(): ImageBitmap? {
        val f = File(ctx.filesDir, "wallpaper.img")
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
    }

    fun setWallpaper(uri: Uri) {
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                File(ctx.filesDir, "wallpaper.img").outputStream().use { input.copyTo(it) }
            }
            wallpaper = loadWallpaper()
        } catch (e: Exception) {
        }
    }

    fun clearWallpaper() {
        File(ctx.filesDir, "wallpaper.img").delete()
        wallpaper = null
    }
}
