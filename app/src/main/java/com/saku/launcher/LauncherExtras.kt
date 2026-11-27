package com.saku.launcher

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LauncherExtras {

    private fun adminComponent(context: Context) =
        ComponentName(context, SakuDeviceAdmin::class.java)

    fun isDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    fun requestDeviceAdmin(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(activity))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Izinkan Saku mengunci layar lewat double-tap.")
        }
        activity.startActivity(intent)
    }

    fun lockNow(context: Context): Boolean {
        if (!isDeviceAdmin(context)) return false
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
        return true
    }

    fun isNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val listener = ComponentName(context, SakuNotificationListener::class.java).flattenToString()
        return enabled.contains(listener)
    }

    fun openNotificationSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openUsageSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun usageRank(context: Context): Map<String, Long> {
        if (!isUsageAccess(context)) return emptyMap()
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 7L * 24 * 60 * 60 * 1000
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?: return emptyMap()
            stats.associate { it.packageName to it.totalTimeInForeground }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun backupFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "saku-backup.json")

    fun exportBackup(context: Context): File {
        val obj = JSONObject().apply {
            put("version", 3)
            put("columns", Prefs.columns)
            put("effect3d", Prefs.effect3d)
            put("notifTiles", Prefs.notifTiles)
            put("usageSort", Prefs.usageSort)
            put("lockGesture", Prefs.lockGesture)
            put("drawerList", Prefs.drawerList)
            put("hidden", JSONArray(Prefs.hidden.toList()))
        }
        val file = backupFile(context)
        file.writeText(obj.toString(2))
        return file
    }

    fun importBackup(context: Context): Boolean {
        val file = backupFile(context)
        if (!file.exists()) return false
        return try {
            val obj = JSONObject(file.readText())
            Prefs.columns = obj.optInt("columns", 4)
            Prefs.effect3d = obj.optBoolean("effect3d", false)
            Prefs.notifTiles = obj.optBoolean("notifTiles", false)
            Prefs.usageSort = obj.optBoolean("usageSort", false)
            Prefs.lockGesture = obj.optBoolean("lockGesture", false)
            Prefs.drawerList = obj.optBoolean("drawerList", false)
            val array = obj.optJSONArray("hidden") ?: JSONArray()
            val hidden = HashSet<String>()
            for (i in 0 until array.length()) hidden.add(array.getString(i))
            Prefs.hidden = hidden
            true
        } catch (_: Exception) {
            false
        }
    }
}
