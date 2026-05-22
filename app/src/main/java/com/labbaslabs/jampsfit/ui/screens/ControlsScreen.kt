package com.labbaslabs.jampsfit.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.MainActivity
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard

@Composable
fun ControlsScreen(
    state: WatchState,
    scrollState: ScrollState = rememberScrollState(),
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val activity = LocalActivity.current as? MainActivity
    var queriedSettings by rememberSaveable { mutableStateOf(false) }
    var controlsTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.isConnected) {
        if (state.isConnected && !queriedSettings) {
            activity?.sendGadgetbridgeProbe("get-alarms")
            activity?.sendGadgetbridgeProbe("get-step-goal")
            activity?.sendGadgetbridgeProbe("get-auto-lock")
            queriedSettings = true
        }
        if (!state.isConnected) queriedSettings = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SecondaryTabRow(selectedTabIndex = controlsTab, containerColor = Color.Transparent, divider = {}) {
            Tab(selected = controlsTab == 0, onClick = { controlsTab = 0 }, text = { Text("Watch") })
            Tab(selected = controlsTab == 1, onClick = { controlsTab = 1 }, text = { Text("App") })
            Tab(selected = controlsTab == 2, onClick = { controlsTab = 2 }, text = { Text("Manual") })
        }

        if (controlsTab == 0) {
        WatchConnectionCard(state, activity, onScanClick, onDisconnectClick)

        SleekCard {
            Text(text = "Watch Actions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { activity?.findWatch() },
                enabled = state.isConnected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Watch, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find My Watch")
            }
        }

        SleekCard {
            Text(text = "Alarms", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            var alarmSlot by remember { mutableIntStateOf(0) }
            var alarmEnabled by remember { mutableStateOf(true) }
            var alarmHour by remember { mutableFloatStateOf(7f) }
            var alarmMinute by remember { mutableFloatStateOf(16f) }
            var alarmRepeat by remember { mutableIntStateOf(0x3E) }
            val selectedAlarm = state.alarmSettings.firstOrNull { it.slot == alarmSlot }

            LaunchedEffect(alarmSlot, selectedAlarm) {
                selectedAlarm?.let {
                    alarmEnabled = it.enabled
                    alarmHour = it.hour.toFloat()
                    alarmMinute = it.minute.toFloat()
                    alarmRepeat = it.repeatMask
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1", "2", "3").forEachIndexed { index, label ->
                    FilterChip(
                        selected = alarmSlot == index,
                        onClick = { alarmSlot = index },
                        label = { Text(label) }
                    )
                }
            }

            SettingSwitch(label = "Enabled", checked = alarmEnabled) { alarmEnabled = it }
            Text("Time: ${alarmHour.toInt().toString().padStart(2, '0')}:${alarmMinute.toInt().toString().padStart(2, '0')}")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hour", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                Slider(value = alarmHour, onValueChange = { alarmHour = it.toInt().toFloat() }, valueRange = 0f..23f, steps = 22, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Min", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                Slider(value = alarmMinute, onValueChange = { alarmMinute = it.toInt().toFloat() }, valueRange = 0f..59f, steps = 58, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Once" to 0x00, "Weekdays" to 0x3E, "Every Day" to 0x7F).forEach { (label, mask) ->
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
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Alarm ${alarmSlot + 1}")
            }
            if (state.alarmSettings.isNotEmpty()) {
                Text(
                    text = "Loaded ${state.alarmSettings.size} alarm${if (state.alarmSettings.size == 1) "" else "s"} from watch.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        SleekCard {
            Text(text = "Display", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            var autoLockSeconds by remember { mutableFloatStateOf((state.autoLockSecondsSetting ?: 20).toFloat()) }
            LaunchedEffect(state.autoLockSecondsSetting) {
                state.autoLockSecondsSetting?.let { autoLockSeconds = it.toFloat() }
            }
            Text("Auto-lock: ${autoLockSeconds.toInt()}s")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(value = autoLockSeconds, onValueChange = { autoLockSeconds = it.toInt().toFloat() }, valueRange = 5f..60f, steps = 10, modifier = Modifier.weight(1f))
                Button(onClick = { activity?.setAutoLockSeconds(autoLockSeconds.toInt()) }, enabled = state.isConnected, shape = RoundedCornerShape(8.dp)) { Text("Send") }
            }
            Text("Time format", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GadgetProbeButton("12h", "time-12h", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("24h", "time-24h", state.isConnected, activity, Modifier.weight(1f))
            }
            Text("Quick View / wrist raise", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { activity?.setQuickViewEnabled(false) },
                    enabled = state.isConnected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Quick Off", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { activity?.setQuickViewEnabled(true) },
                    enabled = state.isConnected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Quick On", fontSize = 12.sp) }
            }
        }

        SleekCard {
            Text(text = "Weather", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { activity?.setWeatherCity("Joensuu") },
                enabled = state.isConnected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Weather On")
            }
            Text(
                text = "Sends the partly confirmed Joensuu weather sequence. Current conditions still need more verification.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        SleekCard {
            Text(text = "Experimental FEE2 Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            var stepGoal by remember { mutableFloatStateOf((state.stepGoalSetting ?: 9000).toFloat()) }
            LaunchedEffect(state.stepGoalSetting) {
                state.stepGoalSetting?.let { stepGoal = it.toFloat() }
            }
            Text("Step goal: ${stepGoal.toInt()}")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(value = stepGoal, onValueChange = { stepGoal = (it / 1000f).toInt() * 1000f }, valueRange = 2000f..35000f, steps = 32, modifier = Modifier.weight(1f))
                Button(onClick = { activity?.setStepGoal(stepGoal.toInt()) }, enabled = state.isConnected, shape = RoundedCornerShape(8.dp)) { Text("Send") }
            }

            OutlinedButton(
                onClick = { activity?.sendWeatherForecastSample() },
                enabled = state.isConnected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Forecast Sample")
            }
            Text("Current weather probes", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WeatherProbeButton("43 Cold", "43-cold", state.isConnected, activity, Modifier.weight(1f))
                WeatherProbeButton("43 Warm", "43-warm", state.isConnected, activity, Modifier.weight(1f))
                WeatherProbeButton("B5 Warm", "b5-warm", state.isConnected, activity, Modifier.weight(1f))
            }

            Text(
                text = "These use the corrected FEE2 route but still need live confirmation.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        SleekCard {
            Text(text = "Step Probes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Real-step candidates", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GadgetProbeButton("33 00", "steps-33-00", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("33 01", "steps-33-01", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("33 02", "steps-33-02", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GadgetProbeButton("59 00", "steps-59-00", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("59 01", "steps-59-01", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("59 02", "steps-59-02", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("59 03", "steps-59-03", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GadgetProbeButton("10/59 00", "steps-10-59-00", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("10/59 01", "steps-10-59-01", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("10/59 02", "steps-10-59-02", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("10/59 03", "steps-10-59-03", state.isConnected, activity, Modifier.weight(1f))
            }
            Text("Advanced/unknown probes", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GadgetProbeButton("B9 Weather", "b9-weather-19", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GadgetProbeButton("B9 Card Cfg", "b9-ecard-config", state.isConnected, activity, Modifier.weight(1f))
                GadgetProbeButton("B9 Card Data", "b9-ecard-content", state.isConnected, activity, Modifier.weight(1f))
            }
            Text(
                text = "HR 6D/HR Stop trigger visible watch HR measurement, and Heartbeat 64 had no visible effect; all three are retired from the UI.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        } else if (controlsTab == 1) {
            AppSettingsControls(state, activity, onScanClick, onDisconnectClick)
        } else {
            ManualCommandControls(state, activity)
        }
    }
}

@Composable
private fun WatchConnectionCard(
    state: WatchState,
    activity: MainActivity?,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    SleekCard {
        Text(text = "Watch Connection", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { if (state.isConnected) onDisconnectClick() else onScanClick() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = if (state.isConnected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
        ) {
            Icon(if (state.isConnected) Icons.Default.BluetoothDisabled else Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isConnected) "Disconnect Watch" else "Scan & Connect")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { activity?.syncTime() }, enabled = state.isConnected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sync Watch Clock")
        }
        OutlinedButton(onClick = { activity?.readBattery() }, enabled = state.isConnected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh Battery Status")
        }
        OutlinedButton(onClick = { activity?.clearQueue() }, enabled = state.isConnected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.ClearAll, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Comm Queue")
        }
    }
}

@Composable
private fun AppSettingsControls(
    state: WatchState,
    activity: MainActivity?,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    SleekCard {
        Text(text = "App Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Border Color", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        val colors = listOf(
            "White" to 0xFFFFFFFF,
            "Blue" to 0xFF2196F3,
            "Green" to 0xFF4CAF50,
            "Red" to 0xFFF44336,
            "Orange" to 0xFFFF9800,
            "Purple" to 0xFF9C27B0,
            "Cyan" to 0xFF00BCD4
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { (name, colorVal) ->
                val color = Color(colorVal)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, RoundedCornerShape(16.dp))
                        .border(
                            2.dp,
                            if (state.borderColor == colorVal.toInt()) Color.White else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { activity?.updateBorderColor(colorVal.toInt()) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Border Thickness: ${"%.1f".format(state.borderThickness)}dp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Slider(
            value = state.borderThickness,
            onValueChange = { activity?.updateBorderThickness(it) },
            valueRange = 0.5f..4.0f,
            steps = 6,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Text("Border Brightness: ${(state.borderAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Slider(
            value = state.borderAlpha,
            onValueChange = { activity?.updateBorderAlpha(it) },
            valueRange = 0.1f..1.0f,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

    SleekCard {
        Text(text = "App Behavior", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitch(label = "Start on Phone Boot", checked = state.autoStart) { activity?.toggleAutoStart(it) }
        SettingSwitch(label = "Persistent Background Service", checked = state.isServiceRunning) {
            if (it) activity?.checkPermissionsAndStart() else activity?.stopWatchService()
        }
        SettingSwitch(label = "Connect Automatically", checked = state.autoConnect) { activity?.toggleAutoConnect(it) }
        SettingSwitch(label = "Mirror Notifications", checked = state.notificationsEnabled) { activity?.toggleNotifications(it) }

        if (state.notificationsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingSwitch(label = "Ignore Duplicate Notifications", checked = state.ignoreDuplicateNotifications) { 
                activity?.toggleIgnoreDuplicates(it) 
            }
            Text(
                text = "Prevents the same notification content from being sent multiple times (remembered for 30 days).",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
            Text(text = "Notification Filters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = "Exclude apps by package name (e.g. com.digibites.accubattery)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            var newPkg by remember { mutableStateOf("") }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPkg,
                    onValueChange = { newPkg = it.trim().lowercase() },
                    label = { Text("Package Name", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                IconButton(onClick = { if (newPkg.isNotBlank()) { activity?.addNotificationFilter(newPkg); newPkg = "" } }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Filter", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                state.notificationFilters.sorted().forEach { pkg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = pkg, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        IconButton(onClick = { activity?.removeNotificationFilter(pkg) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    SleekCard {
        Text(text = "Watch Notifications", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        var customTitle by rememberSaveable { mutableStateOf("jampsFit") }
        var customText by rememberSaveable { mutableStateOf("Hello from your phone") }
        OutlinedTextField(
            value = customTitle,
            onValueChange = { customTitle = it.take(18) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it.take(40) },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { activity?.sendLegacyShortNotification(customTitle, customText) },
                enabled = state.isConnected,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send")
            }
            OutlinedButton(
                onClick = { activity?.sendLegacyCallNotification(customTitle.ifBlank { "Call" }, customText.ifBlank { "Incoming call" }) },
                enabled = state.isConnected,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call")
            }
        }
        SettingSwitch(
            label = "Use Call Format for Incoming Calls",
            checked = state.useLegacyCallNotifications
        ) { activity?.toggleLegacyCallNotifications(it) }
        Text(
            text = "Uses the confirmed short notification and call packet formats; app notification mirroring still obeys the filters above.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }

    SleekCard {
        Text(text = "Battery Notification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Notify at ${state.batteryThreshold}%")
        Slider(value = state.batteryThreshold.toFloat(), onValueChange = { activity?.updateBatteryThreshold(it.toInt()) }, valueRange = 5f..50f)
    }

    SleekCard {
        Text(text = "Da Fit Settings Probes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Auto HR interval", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GadgetProbeButton("HR 10m", "auto-hr-10m", state.isConnected, activity, Modifier.weight(1f))
            GadgetProbeButton("HR 5m", "auto-hr-5m", state.isConnected, activity, Modifier.weight(1f))
        }
        Text("Move reminder", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GadgetProbeButton("Move On", "move-reminder-on", state.isConnected, activity, Modifier.weight(1f))
            GadgetProbeButton("Move Off", "move-reminder-off", state.isConnected, activity, Modifier.weight(1f))
        }
        Text("Weather", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        OutlinedButton(
            onClick = { activity?.sendGadgetbridgeProbe("b9-weather-19") },
            enabled = state.isConnected,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) { Text("Weather Probe", fontSize = 12.sp) }
        Text(
            text = "Weather On moved to Watch > Weather because it partly works; these remain exploratory settings probes.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun ManualCommandControls(state: WatchState, activity: MainActivity?) {
    SleekCard {
        Text(text = "Protocol Configuration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("FE EA 20", "FE EA 10", "FE EA 21").forEach { header ->
                FilterChip(
                    selected = state.protocolHeader == header,
                    onClick = { activity?.updateProtocol(header, state.writeUuidShort, false, state.payloadLengthOnly) },
                    label = { Text(header.split(" ").last(), fontSize = 11.sp) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("fee2", "6387", "fee5", "fee6").forEach { uuid ->
                FilterChip(
                    selected = state.writeUuidShort == uuid,
                    onClick = { activity?.updateProtocol(state.protocolHeader, uuid, false, state.payloadLengthOnly) },
                    label = { Text(uuid, fontSize = 11.sp) }
                )
            }
        }
        SettingSwitch(label = "Length = Payload Only", checked = state.payloadLengthOnly) {
            activity?.updateProtocol(state.protocolHeader, state.writeUuidShort, false, it)
        }
    }

    SleekCard {
        Text(text = "Manual RAW Write", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        var rawCommand by remember { mutableStateOf("") }
        var useNativeChannel by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = rawCommand,
            onValueChange = { rawCommand = it },
            label = { Text(if (useNativeChannel) "Write to 6387" else "Write to selected target: ${state.writeUuidShort}") },
            placeholder = { Text("FE EA 20 05 61", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = useNativeChannel, onCheckedChange = { useNativeChannel = it })
            Text("Use native write channel 6387", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = { rawCommand = "FE EA 20 05 61"; useNativeChannel = false }, label = { Text("Find") })
            AssistChip(onClick = { rawCommand = "FE EA 20 05 21"; useNativeChannel = false }, label = { Text("Get Alarms") })
            AssistChip(onClick = { rawCommand = "FE EA 20 05 26"; useNativeChannel = false }, label = { Text("Goal") })
        }
        Button(
            onClick = { activity?.sendRawTest(rawCommand, useNativeChannel) },
            enabled = state.isConnected && rawCommand.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Send RAW Command")
        }
    }
}

@Composable
private fun NotificationProbeButton(
    label: String,
    kind: String,
    enabled: Boolean,
    activity: MainActivity?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedButton(
        onClick = { activity?.sendNotificationProbe(kind) },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun WeatherProbeButton(
    label: String,
    kind: String,
    enabled: Boolean,
    activity: MainActivity?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedButton(
        onClick = { activity?.sendWeatherCurrentProbe(kind) },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun GadgetProbeButton(
    label: String,
    kind: String,
    enabled: Boolean,
    activity: MainActivity?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedButton(
        onClick = { activity?.sendGadgetbridgeProbe(kind) },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}
