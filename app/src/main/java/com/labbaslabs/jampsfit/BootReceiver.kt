package com.labbaslabs.jampsfit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("jampsFitPrefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("autoStart", false)
            
            if (autoStart) {
                val serviceIntent = Intent(context, WatchService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
