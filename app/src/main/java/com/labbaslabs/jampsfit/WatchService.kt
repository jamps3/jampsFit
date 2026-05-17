package com.labbaslabs.jampsfit

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.Ringtone
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
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.labbaslabs.jampsfit.NOTIFICATION_RECEIVED") {
                val title = intent.getStringExtra("title") ?: ""
                val text = intent.getStringExtra("text") ?: ""
                val state = watchManager.state.value
                if (state.isConnected && state.notificationsEnabled) {
                    watchManager.sendNotification(title, text)
                }
            }
        }
    }

    lateinit var watchManager: WatchManager
        private set

    private lateinit var audioManager: AudioManager
    private lateinit var cameraManager: CameraManager
    private var lastEvent: String? = null
    private var lowBatteryNotified = false
    private var lastConnectionState = false
    private var isFlashlightOn = false
    private var activeRingtone: Ringtone? = null
    private var findPhoneJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "WatchServiceChannel"
        private const val FIND_PHONE_CHANNEL_ID = "FindPhoneChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_NOTIFICATION_ID = 2
        private const val DISCONNECT_NOTIFICATION_ID = 3
        private const val CONNECTED_NOTIFICATION_ID = 4
        private const val TEST_NOTIFICATION_ID = 5
        private const val FIND_PHONE_NOTIFICATION_ID = 6
    }

    override fun onCreate() {
        super.onCreate()
        watchManager = WatchManager(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        createNotificationChannels()
        
        if (watchManager.state.value.autoConnect) {
            watchManager.startScan()
        }

        val filter = IntentFilter("com.labbaslabs.jampsfit.NOTIFICATION_RECEIVED")
        registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        watchManager.state.onEach { state ->
            updateNotification(if (state.isConnected) "Connected" else "Waiting for watch")
            
            // Connection alert logic
            if (state.isConnected != lastConnectionState) {
                if (!state.isConnected && lastConnectionState) {
                    sendDisconnectNotification()
                } else if (state.isConnected) {
                    cancelDisconnectNotification()
                    sendConnectedNotification()
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

            if (state.lastRemoteEvent != null) {
                if (state.lastRemoteEvent != lastEvent) {
                    lastEvent = state.lastRemoteEvent
                    handleRemoteEvent(state.lastRemoteEvent, state.shutterAction, state.musicAction)
                }
            } else {
                lastEvent = null
            }

            handleFindPhoneState(state.isFindingPhone)
        }.launchIn(serviceScope)
    }

    private fun handleFindPhoneState(active: Boolean) {
        if (active) {
            if (findPhoneJob == null) {
                // High-priority notification with full-screen intent to bring app to foreground
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notificationManager = getSystemService(NotificationManager::class.java)
                val notification = NotificationCompat.Builder(this, FIND_PHONE_CHANNEL_ID)
                    .setContentTitle("Find My Phone")
                    .setContentText("Your watch is looking for this phone!")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .build()

                notificationManager.notify(FIND_PHONE_NOTIFICATION_ID, notification)

                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    Log.e("WatchService", "Background startActivity failed: ${e.message}")
                }

                findPhoneJob = serviceScope.launch {
                    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    activeRingtone = RingtoneManager.getRingtone(applicationContext, notification).apply {
                        isLooping = true
                        play()
                    }
                    
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val vibrator = vibratorManager.defaultVibrator

                    while (isActive) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                        delay(2000)
                    }
                }
            }
        } else {
            findPhoneJob?.cancel()
            findPhoneJob = null
            activeRingtone?.stop()
            activeRingtone = null
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(FIND_PHONE_NOTIFICATION_ID)
        }
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
        val isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (isMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
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
        watchManager.setFindingPhone(true)
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

    fun sendConnectedNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("jampsFit Connected!")
            .setContentText("Watch link is ready.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(CONNECTED_NOTIFICATION_ID, notification)
    }

    fun postTestPhoneNotification(kind: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val (title, text) = when (kind) {
            "short" -> "jampsFit Test" to "Short phone notification"
            "long" -> "jampsFit Long Test" to "This longer Android notification tests whether Da Fit mirrors jampsFit notifications safely to the watch."
            "update" -> "jampsFit Update" to "Notification update test ${System.currentTimeMillis() % 100000}"
            else -> "jampsFit Test" to "Phone notification path"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(TEST_NOTIFICATION_ID, notification)
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

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Watch Connection Service",
            NotificationManager.IMPORTANCE_LOW
        )
        
        val findPhoneChannel = NotificationChannel(
            FIND_PHONE_CHANNEL_ID,
            "Find My Phone Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Used for high-priority 'Find My Phone' alerts"
            enableVibration(true)
            setSound(null, null) // Sound is handled by RingtoneManager in the service
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(findPhoneChannel)
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(notificationReceiver)
        watchManager.disconnect()
        super.onDestroy()
    }
}
