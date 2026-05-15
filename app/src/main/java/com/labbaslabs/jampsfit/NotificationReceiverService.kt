package com.labbaslabs.jampsfit

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationReceiverService : NotificationListenerService() {
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (packageName == "com.labbaslabs.jampsfit") return // Don't mirror our own notifications

        Log.d("NotificationReceiver", "Notification from $packageName: $title - $text")
        
        // We'll hook this up to WatchManager once we have the protocol
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: clear notification from watch if supported
    }
}
