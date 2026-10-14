package com.example.liquidlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(val label: String, val packageName: String, val className: String) {
    val key: String get() = "$packageName/$className"
}

/** 홈 화면 한 칸(또는 위젯) 정보. type: "app" | "clock" | "widget"(payload = appWidgetId) */
data class HomeItem(
    val id: String,
    val type: String,
    val col: Int,
    val row: Int,
    val spanC: Int,
    val spanR: Int,
    val payload: String
)

class LauncherStore(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var askedDefault: Boolean
        get() = sp.getBoolean("askedDefault", false)
        set(v) {
            sp.edit().putBoolean("askedDefault", v).apply()
        }

    var aodEnabled: Boolean
        get() = sp.getBoolean("aodEnabled", false)
        set(v) {
            sp.edit().putBoolean("aodEnabled", v).apply()
        }

    /** 마지막 터치 후 대기 화면으로 들어가기까지의 시간(초) */
    var aodIdleSec: Int
        get() = sp.getInt("aodIdleSec", 30)
        set(v) {
            sp.edit().putInt("aodIdleSec", v).apply()
        }

    /** 대기 화면을 유지하는 최대 시간(분). 지나면 화면이 자동으로 꺼진다. */
    var aodMaxMin: Int
        get() = sp.getInt("aodMaxMin", 5)
        set(v) {
            sp.edit().putInt("aodMaxMin", v).apply()
        }

    var aodOnlyCharging: Boolean
        get() = sp.getBoolean("aodOnlyCharging", true)
        set(v) {
            sp.edit().putBoolean("aodOnlyCharging", v).apply()
        }

    fun loadItems(): List<HomeItem> {
        val s = sp.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HomeItem(
                    o.getString("id"), o.getString("type"),
                    o.getInt("col"), o.getInt("row"),
                    o.getInt("spanC"), o.getInt("spanR"),
                    o.getString("payload")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveItems(items: List<HomeItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("type", it.type)
                    .put("col", it.col).put("row", it.row)
                    .put("spanC", it.spanC).put("spanR", it.spanR)
                    .put("payload", it.payload)
            )
        }
        sp.edit().putString("items", arr.toString()).apply()
    }
}
