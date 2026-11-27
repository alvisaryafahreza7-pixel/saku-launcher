package com.saku.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Prefs {
    private const val NAME = "saku_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    var effect3d: Boolean
        get() = sp.getBoolean("effect3d", false)
        set(value) = sp.edit().putBoolean("effect3d", value).apply()

    var notifTiles: Boolean
        get() = sp.getBoolean("notifTiles", false)
        set(value) = sp.edit().putBoolean("notifTiles", value).apply()

    var usageSort: Boolean
        get() = sp.getBoolean("usageSort", false)
        set(value) = sp.edit().putBoolean("usageSort", value).apply()

    var lockGesture: Boolean
        get() = sp.getBoolean("lockGesture", false)
        set(value) = sp.edit().putBoolean("lockGesture", value).apply()

    var drawerList: Boolean
        get() = sp.getBoolean("drawerList", false)
        set(value) = sp.edit().putBoolean("drawerList", value).apply()

    var columns: Int
        get() = sp.getInt("columns", 4).coerceIn(3, 5)
        set(value) = sp.edit().putInt("columns", value.coerceIn(3, 5)).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet("hidden", value).apply()

    fun notifCounts(): Map<String, Int> {
        val raw = sp.getString("notifCounts", "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val out = HashMap<String, Int>()
            obj.keys().forEach { key -> out[key] = obj.optInt(key, 0) }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveNotifCounts(counts: Map<String, Int>) {
        val obj = JSONObject()
        counts.forEach { (key, value) -> obj.put(key, value) }
        sp.edit().putString("notifCounts", obj.toString()).apply()
    }
}
