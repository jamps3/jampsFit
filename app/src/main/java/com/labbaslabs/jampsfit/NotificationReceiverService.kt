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
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        Log.d("NotificationReceiver", "Notification from $packageName: $title - $text")

        // Broadcast the notification to the WatchService
        val intent = Intent("com.labbaslabs.jampsfit.NOTIFICATION_RECEIVED").setPackage(applicationContext.packageName)
        intent.putExtra("title", title)
        intent.putExtra("text", text)
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: clear notification from watch if supported
    }
}
