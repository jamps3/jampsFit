package com.labbaslabs.jampsfit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import androidx.core.content.ContextCompat
import com.labbaslabs.jampsfit.ui.theme.JampsFitTheme
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
        enableEdgeToEdge()
        
        Intent(this, WatchService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        setContent {
            JampsFitTheme {
                val service = watchService
                val state = service?.watchManager?.state?.collectAsState()?.value ?: WatchState()
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                
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
                                    TabSpec("Remote", Icons.Default.SettingsRemote),
                                    TabSpec("Unknown", Icons.Default.Warning),
                                    TabSpec("Settings", Icons.Default.Settings)
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
                                    0 -> HomeScreen(state = state)
                                    1 -> GraphsScreen(state = state)
                                    2 -> RemoteScreen(state = state)
                                    3 -> UnknownScreen(state = state)
                                    4 -> SettingsScreen(
                                        state = state,
                                        onScanClick = { checkPermissionsAndStart() },
                                        onDisconnectClick = { disconnect() }
                                    )
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
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

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

    fun disconnect() {
        watchService?.watchManager?.disconnect()
    }

    fun updateBatteryThreshold(threshold: Int) {
        watchService?.watchManager?.updateBatteryThreshold(threshold)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
