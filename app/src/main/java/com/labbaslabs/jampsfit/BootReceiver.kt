package com.labbaslabs.jampsfit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("jampsFitPrefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("autoStart", true)
            
            if (autoStart && hasBluetoothPermissions(context)) {
                val serviceIntent = Intent(context, WatchService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    private fun hasBluetoothPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
}
