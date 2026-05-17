package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.MainActivity
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard

@Composable
fun ControlsScreen(state: WatchState) {
    val activity = LocalContext.current as? MainActivity

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

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
        }

        SleekCard {
            Text(text = "Experimental FEE2 Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            var autoLockSeconds by remember { mutableFloatStateOf(20f) }
            Text("Auto-lock: ${autoLockSeconds.toInt()}s")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(value = autoLockSeconds, onValueChange = { autoLockSeconds = it.toInt().toFloat() }, valueRange = 5f..60f, steps = 10, modifier = Modifier.weight(1f))
                Button(onClick = { activity?.setAutoLockSeconds(autoLockSeconds.toInt()) }, enabled = state.isConnected, shape = RoundedCornerShape(8.dp)) { Text("Send") }
            }

            var stepGoal by remember { mutableFloatStateOf(9000f) }
            Text("Step goal: ${stepGoal.toInt()}")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(value = stepGoal, onValueChange = { stepGoal = (it / 1000f).toInt() * 1000f }, valueRange = 2000f..35000f, steps = 32, modifier = Modifier.weight(1f))
                Button(onClick = { activity?.setStepGoal(stepGoal.toInt()) }, enabled = state.isConnected, shape = RoundedCornerShape(8.dp)) { Text("Send") }
            }

            var weatherCity by remember { mutableStateOf("Joensuu") }
            OutlinedTextField(
                value = weatherCity,
                onValueChange = { weatherCity = it.take(12) },
                label = { Text("Weather city") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { activity?.setWeatherCity(weatherCity) },
                enabled = state.isConnected && weatherCity.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Weather City")
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
            Text(text = "Notification Probes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            NotificationProbeButton("Legacy Short", "legacy-short", state.isConnected, activity)
            NotificationProbeButton("Legacy Call", "legacy-call", state.isConnected, activity)

            Text("FE EA 20 / 0x08", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("Type 1", "20-08-type1", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("Type 2", "20-08-type2", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("Type 3", "20-08-type3", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("Type 5", "20-08-type5", state.isConnected, activity, Modifier.weight(1f))
            }

            Text("Checksum variants", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("Csum 1", "20-08-csum1", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("Csum 3", "20-08-csum3", state.isConnected, activity, Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = { activity?.sendNotificationProbe("20-41-tiny") },
                enabled = state.isConnected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tiny 0x41")
            }
            Text("0x41 length tests", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("20 chars", "20-41-len20", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("40 chars", "20-41-len40", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("60 chars", "20-41-len60", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("80 chars", "20-41-len80", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("120 chars", "20-41-len120", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("160 chars", "20-41-len160", state.isConnected, activity, Modifier.weight(1f))
            }
            NotificationProbeButton("200 chars", "20-41-len200", state.isConnected, activity)
            Text("Display boundary markers", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("M40", "20-41-marker40", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M60", "20-41-marker60", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M80", "20-41-marker80", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("M100", "20-41-marker100", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M140", "20-41-marker140", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M180", "20-41-marker180", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("M220", "20-41-marker220", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M240", "20-41-marker240", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M249", "20-41-marker249", state.isConnected, activity, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NotificationProbeButton("M232", "20-41-marker232", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M236", "20-41-marker236", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M238", "20-41-marker238", state.isConnected, activity, Modifier.weight(1f))
                NotificationProbeButton("M239", "20-41-marker239", state.isConnected, activity, Modifier.weight(1f))
            }

            Text(
                text = "Test one button at a time and wait a few seconds. Each probe logs exact bytes.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
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
