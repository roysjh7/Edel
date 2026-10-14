package com.example.liquidlauncher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

data class IconImportResult(val applied: Int, val unmatched: List<String>)

/** 설치된 앱 조회·아이콘(기본/커스텀) 캐시·실행 담당 */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val iconCache = HashMap<String, ImageBitmap>()
    private val iconDir: File get() = File(context.filesDir, "icons").also { it.mkdirs() }

    @Suppress("DEPRECATION")
    private fun query(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return query(intent)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    // ---------- 아이콘 ----------
    fun icon(app: AppInfo): ImageBitmap = iconCache.getOrPut(app.key) {
        val custom = customFile(app)
        val fromFile = if (custom.exists()) BitmapFactory.decodeFile(custom.path)?.asImageBitmap() else null
        fromFile ?: systemIcon(app)
    }

    private fun systemIcon(app: AppInfo): ImageBitmap {
        val drawable = try {
            pm.getActivityIcon(ComponentName(app.packageName, app.className))
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        return renderIcon(drawable).asImageBitmap()
    }

    /**
     * 어댑티브 아이콘은 제조사 마스크(원/물방울 등)를 씌우지 않고 배경+전경 레이어를 꽉 채워 그린다.
     * 모양은 UI 쪽에서 ContinuousCornerShape 로 잘라서 모든 아이콘이 iOS 같은 같은 곡률을 갖게 한다.
     */
    private fun renderIcon(d: Drawable): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            val ext = (size * 0.25f).toInt() // 레이어(108dp) 중 보이는 영역(72dp)만 꽉 채우기
            d.background?.let {
                it.setBounds(-ext, -ext, size + ext, size + ext)
                it.draw(canvas)
            }
            d.foreground?.let {
                it.setBounds(-ext, -ext, size + ext, size + ext)
                it.draw(canvas)
            }
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
        }
        return bmp
    }

    // ---------- 커스텀 아이콘 (GPT 등으로 만든 이미지) ----------
    private fun customFile(app: AppInfo) = File(iconDir, app.packageName + ".png")

    /** 이미지를 가운데 기준 정사각형으로 잘라 256px PNG 로 저장한다 */
    fun setCustomIcon(app: AppInfo, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false
            val side = min(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 256, 256, true)
            FileOutputStream(customFile(app)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            iconCache.clear()
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun clearCustomIcon(app: AppInfo) {
        customFile(app).delete()
        iconCache.clear()
    }

    fun clearAllCustomIcons() {
        iconDir.listFiles()?.forEach { it.delete() }
        iconCache.clear()
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * 여러 장을 한 번에 가져오기. 파일 이름(확장자 제외)이 앱의 패키지명 또는 앱 이름과 같으면 그 앱의 아이콘이 된다.
     * 예) com.android.chrome.png, 카메라.png, Camera.png
     */
    fun importIconPack(uris: List<Uri>, apps: List<AppInfo>): IconImportResult {
        var applied = 0
        val unmatched = ArrayList<String>()
        for (u in uris) {
            val name = displayName(u)?.substringBeforeLast('.') ?: continue
            val key = norm(name)
            val target = apps.firstOrNull { norm(it.packageName) == key }
                ?: apps.firstOrNull { norm(it.label) == key }
            if (target != null && setCustomIcon(target, u)) applied++ else unmatched.add(name)
        }
        return IconImportResult(applied, unmatched)
    }

    fun clearCache() = iconCache.clear()

    // ---------- 실행 ----------
    /** 아이콘 위치(bounds)에서 앱 창이 확대되며 열린다 */
    fun launch(app: AppInfo, anchor: View, bounds: android.graphics.Rect): Boolean {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            val opts = ActivityOptions.makeScaleUpAnimation(
                anchor, bounds.left, bounds.top, bounds.width(), bounds.height()
            )
            context.startActivity(i, opts.toBundle())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launch(app: AppInfo): Boolean {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
