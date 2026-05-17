package com.labbaslabs.jampsfit.ui.screens

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.MainActivity
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: WatchState, onScanClick: () -> Unit, onDisconnectClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var settingsTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        TabRow(
            selectedTabIndex = settingsTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            Tab(selected = settingsTab == 0, onClick = { settingsTab = 0 }, text = { Text("Options") })
            Tab(selected = settingsTab == 1, onClick = { settingsTab = 1 }, text = { Text("System Log") })
        }

        if (settingsTab == 0) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SleekCard {
                    Text(text = "Protocol Configuration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Header:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp))
                        listOf("FE EA 20", "FE EA 10", "FE EA 21", "AB 00").forEach { header ->
                            FilterChip(
                                selected = state.protocolHeader == header,
                                onClick = { activity?.updateProtocol(header, state.writeUuidShort, false, state.payloadLengthOnly) },
                                label = { Text(header.split(" ").last(), fontSize = 10.sp) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp))
                        listOf("6387", "fee2", "fee5", "fee6").forEach { uuid ->
                            FilterChip(
                                selected = state.writeUuidShort == uuid,
                                onClick = { activity?.updateProtocol(state.protocolHeader, uuid, false, state.payloadLengthOnly) },
                                label = { Text(uuid, fontSize = 10.sp) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    SettingSwitch(label = "Length = Payload Only", checked = state.payloadLengthOnly) {
                        activity?.updateProtocol(state.protocolHeader, state.writeUuidShort, false, it)
                    }
                }

                SleekCard {
                    Text(text = "Diagnostics & Troubleshooting", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Manual Commands:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    var rawCommand by remember { mutableStateOf("") }
                    var useAltChannel by remember { mutableStateOf(false) }
                    
                    OutlinedTextField(
                        value = rawCommand,
                        onValueChange = { rawCommand = it },
                        placeholder = { Text("FE EA 20 ...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        singleLine = true
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useAltChannel, onCheckedChange = { useAltChannel = it })
                        Text("Use 6487 (Response) Channel", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { activity?.sendRawTest(rawCommand, useAltChannel) },
                        enabled = state.isConnected && rawCommand.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Send RAW Command")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Standard Tests:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { activity?.sendExperimentalNotification() },
                            enabled = state.isConnected,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Exp Notif", fontSize = 10.sp) }
                        Button(
                            onClick = { activity?.sendRawTest("01 01") },
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Handshake", fontSize = 10.sp) }
                    }
                }

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
                    Text(text = "Control Center", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { activity?.findWatch() },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Find My Watch")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { activity?.syncTime() },
                        enabled = state.isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Watch Clock")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { activity?.readBattery() },
                        enabled = state.isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh Battery Status")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { activity?.clearQueue() }, 
                        enabled = state.isConnected,
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { 
                        Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Comm Queue") 
                    }
                }

                SleekCard {
                    Text(text = "App Behavior", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingSwitch(label = "Start on Phone Boot", checked = state.autoStart) { activity?.toggleAutoStart(it) }
                    SettingSwitch(label = "Connect Automatically", checked = state.autoConnect) { activity?.toggleAutoConnect(it) }
                    SettingSwitch(label = "Mirror Notifications", checked = state.notificationsEnabled) { activity?.toggleNotifications(it) }
                }

                SleekCard {
                    Text(text = "Battery Notification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notify at ${state.batteryThreshold}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.batteryThreshold.toFloat(),
                        onValueChange = { activity?.updateBatteryThreshold(it.toInt()) },
                        valueRange = 5f..50f
                    )
                }

                val isConnecting = state.connectionStatus.contains("Connecting", ignoreCase = true)
                Button(
                    onClick = { if (state.isConnected) onDisconnectClick() else onScanClick() },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = if (state.isConnected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    val icon = when {
                        state.isConnected -> Icons.Default.BluetoothDisabled
                        isConnecting -> Icons.Default.BluetoothSearching
                        else -> Icons.Default.BluetoothSearching
                    }
                    Icon(icon, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val text = when {
                        state.isConnected -> "Disconnect Watch"
                        isConnecting -> "Connecting..."
                        else -> "Scan & Connect"
                    }
                    Text(text, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            SleekCard(modifier = Modifier.weight(1f).combinedClickable(
                onClick = {},
                onLongClick = {
                    if (state.debugLog.isNotEmpty()) {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, state.debugLog))) }
                        Toast.makeText(context, "Log copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                }
            )) {
                val scrollState = rememberScrollState()
                LaunchedEffect(state.debugLog) { scrollState.animateScrollTo(scrollState.maxValue) }
                Text(
                    text = state.debugLog,
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
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
