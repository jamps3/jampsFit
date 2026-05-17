package com.labbaslabs.jampsfit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.labbaslabs.jampsfit.ui.theme.JampsFitTheme
import com.labbaslabs.jampsfit.ui.components.SleekCard
import com.labbaslabs.jampsfit.ui.components.SleekNavigationBar
import com.labbaslabs.jampsfit.ui.components.TabSpec
import com.labbaslabs.jampsfit.ui.screens.*

class MainActivity : ComponentActivity() {
    private var watchService: WatchService? by mutableStateOf(null)
    private var isBound by mutableStateOf(false)

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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startWatchService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        
        Intent(this, WatchService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        setContent {
            JampsFitTheme {
                val service = watchService
                val state = service?.watchManager?.state?.collectAsState()?.value ?: WatchState()
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                
                val homeScrollState = rememberScrollState()
                val graphsScrollState = rememberScrollState()
                val controlsScrollState = rememberScrollState()
                val remoteScrollState = rememberScrollState()
                val logsUnknownScrollState = rememberScrollState()
                val logsSystemScrollState = rememberScrollState()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    )) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                val tabs = listOf(
                                    TabSpec("Home", Icons.Default.Home),
                                    TabSpec("Graphs", Icons.Default.Timeline),
                                    TabSpec("Controls", Icons.Default.Tune),
                                    TabSpec("Remote", Icons.Default.SettingsRemote),
                                    TabSpec("Logs", Icons.Default.Article)
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
                                        onDisconnectClick = { disconnect() }
                                    )
                                    3 -> RemoteScreen(state = state, scrollState = remoteScrollState)
                                    4 -> LogsScreen(
                                        state = state,
                                        unknownScrollState = logsUnknownScrollState,
                                        systemScrollState = logsSystemScrollState,
                                        onResetClick = { watchService?.watchManager?.clearUnknownPackets() }
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
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                SleekCard(modifier = Modifier.padding(32.dp)) {
                                    Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Button(
                                            onClick = { watchService?.watchManager?.setFindingPhone(false) },
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

    private fun startWatchService() {
        val intent = Intent(this, WatchService::class.java)
        startForegroundService(intent)
        watchService?.watchManager?.startScan()
    }

    private fun checkPermissionsAndStart() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            startWatchService()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
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
    }

    fun toggleAutoConnect(enabled: Boolean) {
        watchService?.watchManager?.toggleAutoConnect(enabled)
    }

    fun toggleNotifications(enabled: Boolean) {
        watchService?.watchManager?.toggleNotifications(enabled)
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

    fun updateProtocol(header: String, uuid: String, mtu: Boolean, payloadOnly: Boolean) {
        watchService?.watchManager?.updateProtocol(header, uuid, mtu, payloadOnly)
    }

    fun sendTestNotification(cmd: Int = 0x08, type: Int = 0x01) {
        watchService?.watchManager?.sendNotification("jampsFit", "Hello from your phone!", cmd, type)
    }

    fun sendNotificationProbe(kind: String) {
        watchService?.watchManager?.sendNotificationProbe(kind)
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

    fun setStepGoal(goal: Int) {
        watchService?.watchManager?.setStepGoal(goal)
    }

    fun setWeatherCity(city: String) {
        watchService?.watchManager?.setWeatherCity(city)
    }

    fun sendWeatherForecastSample() {
        watchService?.watchManager?.sendWeatherForecastSample()
    }

    fun sendWeatherCurrentProbe(kind: String) {
        watchService?.watchManager?.sendWeatherCurrentProbe(kind)
    }

    fun sendGadgetbridgeProbe(kind: String) {
        watchService?.watchManager?.sendGadgetbridgeProbe(kind)
    }

    fun sendWeightCandidate(weightTenthsKg: Int) {
        watchService?.watchManager?.sendWeightCandidate(weightTenthsKg)
    }

    fun setAlarm1Enabled(enabled: Boolean) {
        watchService?.watchManager?.setAlarm1Enabled(enabled)
    }

    fun setAlarm(slot: Int, enabled: Boolean, hour: Int, minute: Int, repeatMask: Int) {
        watchService?.watchManager?.setAlarm(slot, enabled, hour, minute, repeatMask)
    }

    fun syncHealth() {
        watchService?.watchManager?.syncHealth()
    }

    fun findWatch() {
        watchService?.watchManager?.findWatch()
    }

    fun syncTime() {
        watchService?.watchManager?.syncTime()
    }

    fun queryHealth() {
        watchService?.watchManager?.queryHealth()
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
