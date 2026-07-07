package com.labbaslabs.jampsfit

import android.annotation.SuppressLint
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
import kotlin.time.Duration.Companion.seconds

class WatchService : Service() {
    private val binder = WatchBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val alarmSyncAssistant = AlarmSyncAssistant()
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.getStringExtra("package") ?: ""
            if (intent.action == "com.labbaslabs.jampsfit.NOTIFICATION_RECEIVED") {
                val appName = intent.getStringExtra("appName") ?: pkg
                val title = intent.getStringExtra("title") ?: ""
                val text = intent.getStringExtra("text") ?: ""
                val category = intent.getStringExtra("category") ?: ""
                val state = watchManager.state.value

                watchManager.registerDiscoveredApp(pkg, appName)
                
                if (state.isConnected && state.notificationsEnabled) {
                    if (state.notificationFilters.contains(pkg)) {
                        Log.d("WatchService", "Filtered notification from $pkg")
                        return
                    }

                    val parsed = alarmSyncAssistant.parseNotification(pkg, title, text, category)
                    
                    if (parsed != null && state.autoSyncAlarm && parsed.hour != -1) {
                        Log.d("WatchService", "Alarm sync to ${parsed.hour}:${parsed.minute} (date=${parsed.day}.${parsed.month}) from $pkg")
                        watchManager.setAlarm(slot = 0, enabled = true, hour = parsed.hour, minute = parsed.minute, repeatMask = 0, month = parsed.month, day = parsed.day)
                    }

                    if (state.useLegacyCallNotifications && category == Notification.CATEGORY_CALL) {
                        watchManager.sendLegacyCallNotification(title, text)
                    } else {
                        val isAlarmPkg = pkg.contains("clock") || pkg.contains("alarm")
                        if (isAlarmPkg && state.muteAlarmSyncNotification && (parsed == null || !parsed.isFiring)) {
                            Log.d("WatchService", "Muting watch message for $pkg")
                        } else {
                            if (parsed?.isFiring == true) {
                                val alarmTitle = if (!title.lowercase().contains("alarm") && !title.lowercase().contains("herätys")) "Alarm: $title" else title
                                watchManager.sendNotification(alarmTitle, text, ignoreDuplicate = true, forceMirrored = true)
                                serviceScope.launch { delay(2.seconds); startAlarmVibration() }
                            } else {
                                watchManager.sendNotification(title, text)
                            }
                        }
                    }
                }
            } else if (intent.action == "com.labbaslabs.jampsfit.NOTIFICATION_REMOVED") {
                val isAlarmPkg = pkg.contains("clock") || pkg.contains("alarm") || 
                                pkg == "com.sec.android.app.clockpackage" ||
                                pkg == "com.oneplus.deskclock" ||
                                pkg == "com.coloros.alarmclock" ||
                                pkg == "com.oppo.alarmclock"
                if (isAlarmPkg) {
                    stopAlarmVibration()
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
    private var alarmVibrationJob: Job? = null
    private var watchMissingJob: Job? = null
    private var watchMissingNotified = false

    companion object {
        private const val CHANNEL_ID = "WatchServiceChannel"
        private const val FIND_PHONE_CHANNEL_ID = "FindPhoneChannel"
        private const val WATCH_MISSING_CHANNEL_ID = "WatchMissingChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_NOTIFICATION_ID = 2
        private const val DISCONNECT_NOTIFICATION_ID = 3
        private const val CONNECTED_NOTIFICATION_ID = 4
        private const val FIND_PHONE_NOTIFICATION_ID = 6
        private const val WATCH_MISSING_NOTIFICATION_ID = 7
        private const val WATCH_MISSING_DELAY_MS = 60L * 60L * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        watchManager = WatchManager(this)
        watchManager.setServiceRunning(true)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        createNotificationChannels()
        
        val filter = IntentFilter().apply {
            addAction("com.labbaslabs.jampsfit.NOTIFICATION_RECEIVED")
            addAction("com.labbaslabs.jampsfit.NOTIFICATION_REMOVED")
        }
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)

        watchManager.state.onEach { state ->
            updateNotification(if (state.isConnected) "Connected" else "Waiting for watch")
            handleWatchMissingReminder(state.isConnected)
            
            // Connection alert logic
            if (state.isConnected != lastConnectionState) {
                if (!state.isConnected) {
                    sendDisconnectNotification()
                } else {
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

    private fun handleWatchMissingReminder(isConnected: Boolean) {
        if (isConnected) {
            watchMissingJob?.cancel()
            watchMissingJob = null
            watchMissingNotified = false
            cancelWatchMissingNotification()
            return
        }

        if (watchMissingJob != null || watchMissingNotified) return
        watchMissingJob = serviceScope.launch {
            delay(WATCH_MISSING_DELAY_MS)
            watchMissingJob = null
            if (!watchManager.state.value.isConnected) {
                sendWatchMissingNotification()
                watchMissingNotified = true
            }
        }
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
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val notificationManager = getSystemService(NotificationManager::class.java)
                val canUseFullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    notificationManager.canUseFullScreenIntent()
                val notificationBuilder = NotificationCompat.Builder(this, FIND_PHONE_CHANNEL_ID)
                    .setContentTitle("Find My Phone")
                    .setContentText("Your watch is looking for this phone!")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(false)
                    .setOngoing(true)
                if (canUseFullScreenIntent) {
                    @SuppressLint("LaunchFullIntent")
                    notificationBuilder.setFullScreenIntent(pendingIntent, true)
                }
                val notification = notificationBuilder.build()

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
                    
                    val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val vibrator = vibratorManager.defaultVibrator

                    while (isActive) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                        delay(2.seconds)
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
                    "Volume" -> {
                        repeat(state.volumeSteps) {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_LOWER,
                                AudioManager.FLAG_SHOW_UI,
                            )
                        }
                    }
                    "Utility" -> takeScreenshot()
                    "Custom" -> executeAction(state.prevAction)
                }
            }
            "Next Track" -> {
                when (musicMode) {
                    "Media" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    "Volume" -> {
                        repeat(state.volumeSteps) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                    "Utility" -> openAssistant()
                    "Custom" -> executeAction(state.nextAction)
                }
            }
            "Shutter" -> {
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
        watchManager.setFindingPhone(active = true)
    }

    private fun startAlarmVibration() {
        if (alarmVibrationJob != null) return
        Log.d("WatchService", "Starting repeating alarm vibration on watch")
        alarmVibrationJob = serviceScope.launch {
            while (isActive) {
                if (watchManager.state.value.isConnected) {
                    watchManager.findWatch()
                }
                delay(5.seconds)
            }
        }
    }

    private fun stopAlarmVibration() {
        if (alarmVibrationJob != null) {
            Log.d("WatchService", "Stopping repeating alarm vibration on watch")
            alarmVibrationJob?.cancel()
            alarmVibrationJob = null
        }
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

    private fun sendWatchMissingNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, WATCH_MISSING_CHANNEL_ID)
            .setContentTitle("Watch not seen for 1 hour")
            .setContentText("Check Bluetooth and recharge the watch if needed.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        manager.notify(WATCH_MISSING_NOTIFICATION_ID, notification)
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

    private fun cancelDisconnectNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(DISCONNECT_NOTIFICATION_ID)
    }

    private fun cancelWatchMissingNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(WATCH_MISSING_NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Initializing...")
        startForeground(NOTIFICATION_ID, notification)
        watchManager.ensureAutoConnect()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepRunning = watchManager.state.value.autoStart || watchManager.state.value.autoConnect
        if (keepRunning) {
            val restartIntent = Intent(applicationContext, WatchService::class.java)
            applicationContext.startForegroundService(restartIntent)
        }
        super.onTaskRemoved(rootIntent)
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

        val watchMissingChannel = NotificationChannel(
            WATCH_MISSING_CHANNEL_ID,
            "Watch Missing Reminders",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent reminders when the watch has not been seen for an hour"
            enableVibration(false)
            setSound(null, null)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(findPhoneChannel)
        manager.createNotificationChannel(watchMissingChannel)
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    override fun onDestroy() {
        watchManager.setServiceRunning(false)
        watchMissingJob?.cancel()
        serviceScope.cancel()
        unregisterReceiver(notificationReceiver)
        watchManager.close()
        super.onDestroy()
    }
}
