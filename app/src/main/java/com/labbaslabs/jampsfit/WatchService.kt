package com.labbaslabs.jampsfit

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class WatchService : Service() {
    private val binder = WatchBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    lateinit var watchManager: WatchManager
        private set

    private lateinit var audioManager: AudioManager
    private lateinit var cameraManager: CameraManager
    private var lastEvent: String? = null
    private var lowBatteryNotified = false
    private var lastConnectionState = false
    private var isFlashlightOn = false

    companion object {
        private const val CHANNEL_ID = "WatchServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_NOTIFICATION_ID = 2
        private const val DISCONNECT_NOTIFICATION_ID = 3
    }

    override fun onCreate() {
        super.onCreate()
        watchManager = WatchManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        createNotificationChannel()
        
        if (watchManager.state.value.autoConnect) {
            watchManager.startScan()
        }

        watchManager.state.onEach { state ->
            updateNotification(state.connectionStatus)
            
            // Connection alert logic
            if (state.isConnected != lastConnectionState) {
                if (!state.isConnected && lastConnectionState) {
                    sendDisconnectNotification()
                } else if (state.isConnected) {
                    cancelDisconnectNotification()
                }
                lastConnectionState = state.isConnected
            }
            
            // Low battery notification logic
            state.battery?.let { battery ->
                if (battery <= state.batteryThreshold) {
                    if (!lowBatteryNotified) {
                        sendLowBatteryNotification(battery)
                        lowBatteryNotified = true
                    }
                } else {
                    lowBatteryNotified = false
                }
            }

            if (state.lastRemoteEvent != null && state.lastRemoteEvent != lastEvent) {
                lastEvent = state.lastRemoteEvent
                handleRemoteEvent(state.lastRemoteEvent, state.shutterAction, state.musicAction)
            }
        }.launchIn(serviceScope)
    }

    private fun handleRemoteEvent(event: String, shutterAction: String, musicMode: String) {
        val state = watchManager.state.value
        when (event) {
            "Play/Pause" -> {
                when (musicMode) {
                    "Media" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    "Volume" -> toggleMute()
                    "Utility" -> toggleFlashlight()
                    "Custom" -> executeAction(state.playPauseAction)
                }
            }
            "Previous Track" -> {
                when (musicMode) {
                    "Media" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    "Volume" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "Utility" -> takeScreenshot()
                    "Custom" -> executeAction(state.prevAction)
                }
            }
            "Next Track" -> {
                when (musicMode) {
                    "Media" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    "Volume" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "Utility" -> openAssistant()
                    "Custom" -> executeAction(state.nextAction)
                }
            }
            "Wrist Shake / Shutter" -> {
                when (shutterAction) {
                    "FindMyPhone" -> findMyPhone()
                    "Camera" -> sendMediaKey(KeyEvent.KEYCODE_VOLUME_UP)
                    "Media" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
        }
    }

    private fun executeAction(action: String) {
        when (action) {
            "Play/Pause" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "Next Track" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "Previous Track" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "Volume Up" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "Volume Down" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "Mute" -> toggleMute()
            "Flashlight" -> toggleFlashlight()
            "Assistant" -> openAssistant()
            "Screenshot" -> takeScreenshot()
        }
    }

    private fun toggleMute() {
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } else false
        @Suppress("DEPRECATION")
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, !isMuted)
    }

    private fun toggleFlashlight() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(cameraId, isFlashlightOn)
        } catch (e: Exception) {
            Log.e("WatchService", "Flashlight error: ${e.message}")
        }
    }

    private fun openAssistant() {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("WatchService", "Assistant error: ${e.message}")
        }
    }

    private fun takeScreenshot() {
        // System-wide screenshots usually require accessibility or root.
        // We'll simulate the system key, but results vary.
        sendMediaKey(KeyEvent.KEYCODE_SYSRQ)
    }

    private fun sendMediaKey(keyCode: Int) {
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    private fun findMyPhone() {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = RingtoneManager.getRingtone(applicationContext, notification)
        r.isLooping = false
        r.play()
        
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun sendLowBatteryNotification(battery: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Watch Battery Low")
            .setContentText("Your watch is at $battery%. Please charge soon.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(LOW_BATTERY_NOTIFICATION_ID, notification)
    }

    private fun sendDisconnectNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Watch Disconnected")
            .setContentText("The connection to your watch was lost. Reconnecting...")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
        manager.notify(DISCONNECT_NOTIFICATION_ID, notification)
    }

    private fun cancelDisconnectNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(DISCONNECT_NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Initializing...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class WatchBinder : Binder() {
        fun getService(): WatchService = this@WatchService
    }

    private fun createNotification(status: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("jampsFit Tracking")
            .setContentText("Status: $status")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Watch Connection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        watchManager.disconnect()
        super.onDestroy()
    }
}
