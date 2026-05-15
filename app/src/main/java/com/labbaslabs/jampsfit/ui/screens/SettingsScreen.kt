package com.labbaslabs.jampsfit.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.MainActivity
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard

@Composable
fun SettingsScreen(state: WatchState, onScanClick: () -> Unit, onDisconnectClick: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        SleekCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "Watch Firmware", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(text = state.firmwareVersion ?: "Not detected yet (Reconnect watch)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        SleekCard {
            Text(text = "Permissions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Notification Access")
            }
            Text(
                text = "Required for Notification Mirroring to work.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        SleekCard {
            Text(text = "App Behavior", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            SettingSwitch(label = "Start on Phone Boot", checked = state.autoStart) { (context as? MainActivity)?.toggleAutoStart(it) }
            SettingSwitch(label = "Connect Automatically", checked = state.autoConnect) { (context as? MainActivity)?.toggleAutoConnect(it) }
        }

        SleekCard {
            Text(text = "Battery Notification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Notify at ${state.batteryThreshold}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.batteryThreshold.toFloat(),
                onValueChange = { (context as? MainActivity)?.updateBatteryThreshold(it.toInt()) },
                valueRange = 5f..50f
            )
        }

        Button(
            onClick = { if (state.isConnected) onDisconnectClick() else onScanClick() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = if (state.isConnected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
        ) {
            Icon(if (state.isConnected) Icons.Default.BluetoothDisabled else Icons.Default.BluetoothSearching, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isConnected) "Disconnect Watch" else "Scan & Connect", fontWeight = FontWeight.Bold)
        }

        Text(text = "System Debug", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        SleekCard(modifier = Modifier.weight(1f)) {
            val scrollState = rememberScrollState()
            LaunchedEffect(state.debugLog) { scrollState.animateScrollTo(scrollState.maxValue) }
            Text(text = state.debugLog, modifier = Modifier.fillMaxSize().verticalScroll(scrollState), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
