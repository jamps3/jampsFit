package com.labbaslabs.jampsfit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import com.labbaslabs.jampsfit.ui.theme.JampsFitTheme
import com.labbaslabs.jampsfit.ui.components.SleekCard
import com.labbaslabs.jampsfit.ui.components.SleekNavigationBar
import com.labbaslabs.jampsfit.ui.components.TabSpec
import com.labbaslabs.jampsfit.ui.screens.*

val LocalWatchState = compositionLocalOf { WatchState() }

class MainActivity : ComponentActivity() {
    private var watchService: WatchService? by mutableStateOf(null)
    private var isBound by mutableStateOf(value = false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WatchService.WatchBinder
            watchService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            watchService = null
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.all { it.value }) {
            startWatchService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        
        Intent(this, WatchService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
        startWatchServiceIfAutoConnectEnabled()

        setContent {
            JampsFitTheme {
                val service = watchService
                val state = service?.watchManager?.state?.collectAsState()?.value ?: WatchState()
                
                CompositionLocalProvider(LocalWatchState provides state) {
                    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                
                val homeScrollState = rememberScrollState()
                val graphsScrollState = rememberScrollState()
                val controlsScrollState = rememberScrollState()
                val remoteScrollState = rememberScrollState()
                val logsUnknownScrollState = rememberScrollState()
                val logsSystemScrollState = rememberScrollState()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                val tabs = listOf(
                                    TabSpec("Home", Icons.Default.Home),
                                    TabSpec("Graphs", Icons.Default.Timeline),
                                    TabSpec("Controls", Icons.Default.Tune),
                                    TabSpec("Remote", Icons.Default.SettingsRemote),
                                    TabSpec("Logs", Icons.AutoMirrored.Filled.Article)
                                )
                                SleekNavigationBar(
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it },
                                    tabs = tabs
                                )
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (selectedTab) {
                                    0 -> HomeScreen(state = state, scrollState = homeScrollState)
                                    1 -> GraphsScreen(state = state, scrollState = graphsScrollState)
                                    2 -> ControlsScreen(
                                        state = state,
                                        scrollState = controlsScrollState,
                                        onScanClick = { checkPermissionsAndStart() },
                                    ) { disconnect() }
                                    3 -> RemoteScreen(state = state, scrollState = remoteScrollState)
                                    4 -> LogsScreen(
                                        state = state,
                                        unknownScrollState = logsUnknownScrollState,
                                        systemScrollState = logsSystemScrollState,
                                        onResetClick = {
                                            watchService?.watchManager?.clearUnknownPackets()
                                        }
                                    )
                                }
                            }
                        }

                        if (state.isFindingPhone) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                SleekCard(modifier = Modifier.padding(32.dp)) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Finding Phone...",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Your watch is looking for this phone.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = { watchService?.watchManager?.setFindingPhone(active = false) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Stop Ringing")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        watchService?.watchManager?.checkFullScreenIntentPermission()
    }

    private fun startWatchService() {
        val intent = Intent(this, WatchService::class.java)
        startForegroundService(intent)
        watchService?.watchManager?.startScan()
    }

    private fun startWatchServiceIfAutoConnectEnabled() {
        val prefs = getSharedPreferences("jampsFitPrefs", MODE_PRIVATE)
        val autoConnect = prefs.getBoolean("autoConnect", true)
        if (autoConnect && hasWatchServicePermissions()) {
            startWatchService()
        }
    }

    fun stopWatchService() {
        val intent = Intent(this, WatchService::class.java)
        stopService(intent)
    }

    fun checkPermissionsAndStart() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        if (hasWatchServicePermissions()) {
            startWatchService()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun hasWatchServicePermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
        if (enabled) {
            checkPermissionsAndStart()
        }
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
            } catch (e: Exception) {
                startActivity(Intent("android.settings.MANAGE_FULL_SCREEN_INTENT"))
            }
        }
    }

    fun queryCurrentSteps() {
        watchService?.watchManager?.queryCurrentSteps()
    }

    fun querySleepBoundaries() {
        watchService?.watchManager?.querySleepBoundaries()
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
