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
import androidx.activity.viewModels
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import com.labbaslabs.jampsfit.ui.theme.JampsFitTheme
import com.labbaslabs.jampsfit.ui.components.SleekNavigationBar
import com.labbaslabs.jampsfit.ui.components.TabSpec
import com.labbaslabs.jampsfit.ui.screens.*
import com.labbaslabs.jampsfit.ui.MainViewModel

val LocalWatchState = compositionLocalOf { WatchState() }
val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> { error("No MainViewModel provided") }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var watchService: WatchService? by mutableStateOf(null)
    private var isBound by mutableStateOf(value = false)
    private var pendingScanRequest = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WatchService.WatchBinder
            val s = binder.getService()
            watchService = s
            isBound = true
            
            viewModel.setWatchManager(s.watchManager)
            s.watchManager.setAppForegrounded(true)
            
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
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                
                CompositionLocalProvider(
                    LocalWatchState provides state,
                    LocalMainViewModel provides viewModel
                ) {
                    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            SleekNavigationBar(
                                selectedTab = currentTabIndex,
                                onTabSelected = { currentTabIndex = it },
                                tabs = listOf(
                                    TabSpec("Watch", Icons.Default.Watch),
                                    TabSpec("Eat", Icons.Default.Restaurant),
                                    TabSpec("Progress", Icons.Default.EmojiEvents),
                                    TabSpec("Charts", Icons.Default.BarChart),
                                    TabSpec("Remote", Icons.Default.SettingsRemote),
                                    TabSpec("Settings", Icons.Default.Settings),
                                ),
                            )
                        },
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentTabIndex) {
                                0 -> HomeScreen(state)
                                1 -> EatScreen(state)
                                2 -> GamificationScreen(state)
                                3 -> GraphsScreen(state)
                                4 -> RemoteScreen(state)
                                5 -> ControlsScreen(
                                    state = state,
                                    onScanClick = { viewModel.startScan() },
                                    onDisconnectClick = { viewModel.disconnect() },
                                )
                            }
                        }

                        if (state.isFindingPhone) {
                            AlertDialog(
                                onDismissRequest = { viewModel.setFindingPhone(active = false) },
                                title = { Text("Finding Phone") },
                                text = { Text("Your watch is currently looking for this phone!") },
                                confirmButton = {
                                    Button(onClick = { viewModel.setFindingPhone(active = false) }) {
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
        watchService?.watchManager?.setAppForegrounded(true)
    }

    override fun onStop() {
        watchService?.watchManager?.setAppForegrounded(false)
        super.onStop()
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

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
