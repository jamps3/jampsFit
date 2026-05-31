package com.labbaslabs.jampsfit

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.Intent

class NotificationReceiverService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == "com.labbaslabs.jampsfit") return 

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val category = sbn.notification.category ?: ""

        if (title.isBlank() && text.isBlank()) return
        
        val isAlarmOrCall = category == "alarm" || category == "call" || 
                           packageName.contains("clock") || packageName.contains("alarm")
        
        if (sbn.isOngoing && !isAlarmOrCall) return

        val pm = applicationContext.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }

        Log.d("NotificationReceiver", "Notification from $packageName ($appName): $title - $text")

        // Broadcast the notification to the WatchService
        val intent = Intent("com.labbaslabs.jampsfit.NOTIFICATION_RECEIVED").setPackage(applicationContext.packageName)
        intent.putExtra("package", packageName)
        intent.putExtra("appName", appName)
        intent.putExtra("title", title)
        intent.putExtra("text", text)
        intent.putExtra("category", category)
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == "com.labbaslabs.jampsfit") return

        val intent = Intent("com.labbaslabs.jampsfit.NOTIFICATION_REMOVED").setPackage(applicationContext.packageName)
        intent.putExtra("package", packageName)
        sendBroadcast(intent)
    }
}
