package com.labbaslabs.jampsfit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.*
import com.labbaslabs.jampsfit.ui.theme.JampsFitTheme
import com.labbaslabs.jampsfit.ui.components.SleekNavigationBar
import com.labbaslabs.jampsfit.ui.components.TabSpec
import com.labbaslabs.jampsfit.ui.screens.*

val LocalWatchState = compositionLocalOf { WatchState() }

class MainActivity : ComponentActivity() {
    private var watchService: WatchService? by mutableStateOf(null)
    private var isBound by mutableStateOf(value = false)
    private var pendingScanRequest = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WatchService.WatchBinder
            val s = binder.getService()
            watchService = s
            isBound = true
            
            if (pendingScanRequest) {
                s.watchManager.startScan()
                pendingScanRequest = false
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            watchService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.all { it.value }) {
            startWatchServiceIfAutoConnectEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startWatchServiceIfAutoConnectEnabled()
        }
        requestIgnoreBatteryOptimizations()

        setContent {
            JampsFitTheme {
                val state = watchService?.watchManager?.state?.collectAsState()?.value ?: WatchState()
                
                CompositionLocalProvider(LocalWatchState provides state) {
                    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            SleekNavigationBar(
                                selectedTab = currentTabIndex,
                                onTabSelected = { currentTabIndex = it },
                                tabs = listOf(
                                    TabSpec("Home", Icons.Default.Home),
                                    TabSpec("Remote", Icons.Default.SettingsRemote),
                                    TabSpec("Debug", Icons.Default.BugReport),
                                    TabSpec("Settings", Icons.Default.Settings),
                                ),
                            )
                        },
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentTabIndex) {
                                0 -> HomeScreen(state)
                                1 -> RemoteScreen(state)
                                2 -> LogsScreen(state) { clearUnknownPackets() }
                                3 -> ControlsScreen(
                                    state = state,
                                    onScanClick = { startScan() },
                                    onDisconnectClick = { disconnect() },
                                )
                            }
                        }

                        if (state.isFindingPhone) {
                            AlertDialog(
                                onDismissRequest = { findPhone(active = false) },
                                title = { Text("Finding Phone") },
                                text = { Text("Your watch is currently looking for this phone!") },
                                confirmButton = {
                                    Button(onClick = { findPhone(active = false) }) {
                                        Text("I found it!")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startWatchServiceIfAutoConnectEnabled() {
        val prefs = getSharedPreferences("jampsFitPrefs", MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autoStart", true)
        val autoConnect = prefs.getBoolean("autoConnect", true)
        
        if (autoStart || autoConnect) {
            startWatchService()
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, WatchService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    fun startWatchService() {
        val intent = Intent(this, WatchService::class.java)
        startForegroundService(intent)
        
        val service = watchService
        if (service != null) {
            service.watchManager.startScan()
        } else {
            pendingScanRequest = true
        }
    }

    fun stopWatchService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val intent = Intent(this, WatchService::class.java)
        stopService(intent)
        watchService = null
    }

    fun checkPermissionsAndStart() {
        if (hasPermissions()) {
            startWatchService()
        } else {
            requestPermissions()
        }
    }

    fun startScan() {
        watchService?.watchManager?.startScan()
    }

    fun updateShutterAction(action: String) {
        watchService?.watchManager?.updateShutterAction(action)
    }

    fun updateMusicAction(action: String) {
        watchService?.watchManager?.updateMusicAction(action)
    }

    fun updateCustomAction(button: String, action: String) {
        watchService?.watchManager?.updateCustomAction(button, action)
    }

    fun toggleAutoStart(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoStart(enabled)
    }

    fun toggleAutoConnect(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoConnect(enabled)
    }

    fun toggleAutoSyncAlarm(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoSyncAlarm(enabled)
    }

    fun toggleAutoSyncTime(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoSyncTime(enabled)
    }

    fun updateSyncTimeInterval(hours: Int) {
        watchService?.watchManager?.updateSyncTimeInterval(hours)
    }

    fun toggleMuteAlarmSyncNotification(enabled: Boolean) {
        watchService?.watchManager?.toggleMuteAlarmSyncNotification(enabled)
    }

    fun toggleAutoFetchSteps(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoFetchSteps(enabled)
    }

    fun toggleAutoFetchBattery(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoFetchBattery(enabled)
    }

    fun toggleAutoFetchSleep(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoFetchSleep(enabled)
    }

    fun updateStepFetchInterval(minutes: Int) {
        watchService?.watchManager?.updateStepFetchInterval(minutes)
    }

    fun setAutoHeartRateInterval(minutes: Int) {
        watchService?.watchManager?.setAutoHeartRateInterval(minutes)
    }

    fun toggleNotifications(enabled: Boolean) {
        watchService?.watchManager?.toggleNotifications(enabled)
    }

    fun toggleIgnoreDuplicates(enabled: Boolean) {
        watchService?.watchManager?.toggleIgnoreDuplicates(enabled)
    }

    fun toggleLegacyCallNotifications(enabled: Boolean) {
        watchService?.watchManager?.toggleLegacyCallNotifications(enabled)
    }

    fun addNotificationFilter(pkg: String) {
        watchService?.watchManager?.addNotificationFilter(pkg)
    }

    fun removeNotificationFilter(pkg: String) {
        watchService?.watchManager?.removeNotificationFilter(pkg)
    }

    fun updateBorderColor(color: Int) {
        watchService?.watchManager?.updateBorderColor(color)
    }

    fun updateBorderThickness(thickness: Float) {
        watchService?.watchManager?.updateBorderThickness(thickness)
    }

    fun updateBorderAlpha(alpha: Float) {
        watchService?.watchManager?.updateBorderAlpha(alpha)
    }

    fun updateProfile(gender: String, heightCm: Int, weightKg: Float, ageYears: Int) {
        watchService?.watchManager?.updateProfile(gender, heightCm, weightKg, ageYears)
    }

    fun updateVolumeSteps(steps: Int) {
        watchService?.watchManager?.updateVolumeSteps(steps)
    }

    fun disconnect() {
        watchService?.watchManager?.disconnect()
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                @Suppress("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (_: Exception) {
                // Fallback for some devices/OS versions
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {}
            }
        }
    }

    fun updateBatteryThreshold(threshold: Int) {
        watchService?.watchManager?.updateBatteryThreshold(threshold)
    }

    fun updateProtocol(header: String, uuid: String, payloadOnly: Boolean) {
        watchService?.watchManager?.updateProtocol(header, uuid, payloadOnly)
    }

    fun sendLegacyShortNotification(title: String, text: String) {
        watchService?.watchManager?.sendLegacyShortNotification(title, text)
    }

    fun sendLegacyCallNotification(title: String, text: String) {
        watchService?.watchManager?.sendLegacyCallNotification(title, text)
    }

    fun sendExperimentalNotification() {
        watchService?.watchManager?.sendExperimentalNotification()
    }

    fun prepareDaFitSession() {
        watchService?.watchManager?.prepareDaFitSession()
    }

    fun prepareAndFindWatch() {
        watchService?.watchManager?.prepareAndFindWatch()
    }

    fun sendStartupPreamblePhase1() {
        watchService?.watchManager?.sendStartupPreamblePhase1()
    }

    fun sendStartupPreamblePhase2() {
        watchService?.watchManager?.sendStartupPreamblePhase2()
    }

    fun postTestPhoneNotification(kind: String) {
        watchService?.postTestPhoneNotification(kind)
    }

    fun sendRawTest(hex: String, useAlt: Boolean = false) {
        watchService?.watchManager?.sendRawTest(hex, useAlt)
    }

    fun readBattery() {
        watchService?.watchManager?.readBattery()
    }

    fun clearQueue() {
        watchService?.watchManager?.clearQueue()
    }

    fun setAutoLockSeconds(seconds: Int) {
        watchService?.watchManager?.setAutoLockSeconds(seconds)
    }

    fun setQuickViewWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        watchService?.watchManager?.setQuickViewWindow(startHour, startMinute, endHour, endMinute)
    }

    fun setStepGoal(goal: Int) {
        watchService?.watchManager?.setStepGoal(goal)
    }

    fun setWeatherCity(city: String) {
        watchService?.watchManager?.setWeatherCity(city)
    }

    fun sendWeatherForecastSample() {
        watchService?.watchManager?.sendWeatherForecastSample()
    }

    fun findPhone(active: Boolean) {
        watchService?.watchManager?.setFindingPhone(active)
    }

    fun sendGadgetbridgeProbe(kind: String) {
        watchService?.watchManager?.sendGadgetbridgeProbe(kind)
    }

    fun sendWeightCandidate() {
        watchService?.watchManager?.sendWeightCandidate()
    }

    fun setAlarm(slot: Int, enabled: Boolean, hour: Int, minute: Int, repeatMask: Int) {
        watchService?.watchManager?.setAlarm(slot, enabled, hour, minute, repeatMask)
    }

    fun findWatch() {
        watchService?.watchManager?.findWatch()
    }

    fun syncTime() {
        watchService?.watchManager?.syncTime()
    }

    fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent("android.settings.MANAGE_FULL_SCREEN_INTENT").apply {
                data = Uri.fromParts("package", packageName, null)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent("android.settings.MANAGE_FULL_SCREEN_INTENT"))
                } catch (_: Exception) {}
            }
        }
    }

    fun queryCurrentSteps() {
        watchService?.watchManager?.queryCurrentSteps()
    }

    fun querySleepBoundaries() {
        watchService?.watchManager?.querySleepBoundaries()
    }

    fun clearUnknownPackets() {
        watchService?.watchManager?.clearUnknownPackets()
    }

    fun exportData() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        scope.launch {
            val csv = watchService?.watchManager?.exportDataToCsv() ?: return@launch
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, csv)
                putExtra(Intent.EXTRA_TITLE, "jampsFit Health Data")
                type = "text/csv"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export Health Data")
            startActivity(shareIntent)
        }
    }

    fun startMeasurement(type: String) {
        watchService?.watchManager?.startMeasurement(type)
    }

    fun stopMeasurement() {
        watchService?.watchManager?.stopMeasurement()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
