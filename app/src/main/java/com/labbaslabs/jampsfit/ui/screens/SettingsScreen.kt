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
                    
                    Text("Manual RAW Write:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    var rawCommand by remember { mutableStateOf("") }
                    var useNativeChannel by remember { mutableStateOf(true) }
                    
                    OutlinedTextField(
                        value = rawCommand,
                        onValueChange = { rawCommand = it },
                        label = { Text(if (useNativeChannel) "Write to 6387" else "Write to selected target: ${state.writeUuidShort}") },
                        placeholder = { Text("Example: FE EA 20 05 61", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        singleLine = true
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useNativeChannel, onCheckedChange = { useNativeChannel = it })
                        Text("Use native write channel 6387", style = MaterialTheme.typography.bodySmall)
                    }

                    Text("This sends exactly the bytes shown. Da Fit captures now show most FE EA 20 writes use FEE2; use 6387 only for packets proven on that characteristic.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { rawCommand = "FE EA 20 05 61"; useNativeChannel = true },
                            label = { Text("Fill Find") }
                        )
                        AssistChip(
                            onClick = { rawCommand = "FE EA 20 06 5A 00"; useNativeChannel = true },
                            label = { Text("Fill 5A00") }
                        )
                        AssistChip(
                            onClick = { rawCommand = "FE EA 10 04 2F"; useNativeChannel = false },
                            label = { Text("Fill FEE2 2F") }
                        )
                    }

                    Button(
                        onClick = { activity?.sendRawTest(rawCommand, useNativeChannel) },
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
                            onClick = { activity?.prepareDaFitSession() },
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Find Prep", fontSize = 10.sp) }
                        Button(
                            onClick = { activity?.prepareAndFindWatch() },
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Prep + Find", fontSize = 10.sp) }
                    }
                    Text("Disabled: old prep tests wrote Da Fit FEE2 traffic to the wrong characteristic.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { activity?.sendStartupPreamblePhase1() },
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Start P1", fontSize = 10.sp) }
                        Button(
                            onClick = { activity?.sendStartupPreamblePhase2() },
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Start P2", fontSize = 10.sp) }
                    }
                    Text("Disabled: old startup tests targeted the wrong characteristic.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { activity?.sendExperimentalNotification() },
                            enabled = false,
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

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Android Notification Tests:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { activity?.postTestPhoneNotification("short") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Short", fontSize = 10.sp) }
                        Button(
                            onClick = { activity?.postTestPhoneNotification("long") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Long", fontSize = 10.sp) }
                        Button(
                            onClick = { activity?.postTestPhoneNotification("update") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("Update", fontSize = 10.sp) }
                    }
                    Text("These post normal Android notifications for Da Fit to mirror; they do not write BLE directly.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Captured Da Fit Tests:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    var autoLockSeconds by remember { mutableFloatStateOf(20f) }
                    Text("Auto-lock: ${autoLockSeconds.toInt()}s", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = autoLockSeconds,
                            onValueChange = { autoLockSeconds = it },
                            valueRange = 5f..60f,
                            steps = 10,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { activity?.setAutoLockSeconds(autoLockSeconds.toInt()) },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("Send", fontSize = 12.sp) }
                    }
                    Text("Disabled: 5s/20s rebooted this watch.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                    var stepGoal by remember { mutableFloatStateOf(9000f) }
                    Text("Step goal: ${stepGoal.toInt()}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = stepGoal,
                            onValueChange = { stepGoal = (it / 1000f).toInt() * 1000f },
                            valueRange = 2000f..35000f,
                            steps = 32,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { activity?.setStepGoal(stepGoal.toInt()) },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("Send", fontSize = 12.sp) }
                    }
                    Text("Disabled: captured step-goal write rebooted this watch.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                    var weatherCity by remember { mutableStateOf("Joensuu") }
                    OutlinedTextField(
                        value = weatherCity,
                        onValueChange = { weatherCity = it.take(12) },
                        label = { Text("Weather city", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { activity?.setWeatherCity(weatherCity) },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Send Weather City") }
                    Text("Disabled: captured weather sequence rebooted this watch.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                    var weightKg by remember { mutableFloatStateOf(851f) }
                    Text("Weight candidate: ${"%.1f".format(weightKg / 10f)}kg", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = weightKg,
                            onValueChange = { weightKg = it },
                            valueRange = 500f..1400f,
                            steps = 899,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { activity?.sendWeightCandidate(weightKg.toInt()) },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("Send", fontSize = 12.sp) }
                    }
                    Text("Disabled until a second known weight capture confirms the encoding.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                    Text("Alarm Test:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    var alarmSlot by remember { mutableIntStateOf(0) }
                    var alarmEnabled by remember { mutableStateOf(true) }
                    var alarmHour by remember { mutableFloatStateOf(7f) }
                    var alarmMinute by remember { mutableFloatStateOf(16f) }
                    var alarmRepeat by remember { mutableIntStateOf(0x3E) }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1", "2", "3").forEachIndexed { index, label ->
                            FilterChip(
                                selected = alarmSlot == index,
                                onClick = { alarmSlot = index },
                                label = { Text(label) }
                            )
                        }
                    }
                    SettingSwitch(label = "Alarm Enabled", checked = alarmEnabled) { alarmEnabled = it }
                    Text("Time: ${alarmHour.toInt().toString().padStart(2, '0')}:${alarmMinute.toInt().toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hour", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        Slider(value = alarmHour, onValueChange = { alarmHour = it.toInt().toFloat() }, valueRange = 0f..23f, steps = 22, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Min", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        Slider(value = alarmMinute, onValueChange = { alarmMinute = it.toInt().toFloat() }, valueRange = 0f..59f, steps = 58, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "Once" to 0x00,
                            "Weekdays" to 0x3E,
                            "Every Day" to 0x7F
                        ).forEach { (label, mask) ->
                            FilterChip(
                                selected = alarmRepeat == mask,
                                onClick = { alarmRepeat = mask },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = { activity?.setAlarm(alarmSlot, alarmEnabled, alarmHour.toInt(), alarmMinute.toInt(), alarmRepeat) },
                        enabled = state.isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Send Alarm ${alarmSlot + 1}") }
                    Text("Sends captured Da Fit alarm record via FEE2. Test one change at a time.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                        enabled = state.isConnected,
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
