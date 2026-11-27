package com.saku.launcher

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SakuNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        recount()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        recount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        recount()
    }

    private fun recount() {
        val counts = HashMap<String, Int>()
        try {
            activeNotifications?.forEach { notification ->
                counts[notification.packageName] = (counts[notification.packageName] ?: 0) + 1
            }
        } catch (_: Exception) {
            // Izin bisa dicabut kapan saja; biarkan UI memakai data terakhir.
        }
        Prefs.saveNotifCounts(counts)
        sendBroadcast(Intent(ACTION_NOTIF).setPackage(packageName))
    }

    companion object {
        const val ACTION_NOTIF = "com.saku.launcher.NOTIF_CHANGED"
    }
}
