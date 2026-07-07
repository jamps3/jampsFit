package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.R
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.DataCard
import com.labbaslabs.jampsfit.ui.components.DancingEventControlCard
import com.labbaslabs.jampsfit.ui.components.CurrentWatchExerciseCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

@Composable
fun HomeScreen(state: WatchState, scrollState: ScrollState = rememberScrollState()) {
    val viewModel = LocalMainViewModel.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.jampsfit_logo),
                contentDescription = "jampsFit Logo",
                modifier = Modifier.height(60.dp).weight(1f)
            )
            Text(
                text = "2.0",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFC107)
            )
        }
        
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier.border(
                1.dp,
                if (state.isConnected) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color(state.borderColor).copy(alpha = 0.4f),
                RoundedCornerShape(32.dp)
            )
        ) {
            Text(
                text = state.connectionStatus,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.isConnected) Color(0xFF4CAF50) else Color.Gray
            )
        }

        if (state.deviceName != null) {
            Text(text = state.deviceName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        state.lastWatchSeenTime?.let {
            Text(text = "Last seen ${relativeTimeLabel(it)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        DancingEventControlCard(
            state = state,
            onStart = { viewModel.startDancingEvent() },
            onStop = { viewModel.stopActiveEvent() }
        )

        CurrentWatchExerciseCard(state)

        DataCard(
            label = "Battery",
            value = state.battery?.let { "$it%" } ?: "--",
            supportingText = state.batteryEstimation,
            icon = Icons.Default.BatteryChargingFull,
            color = Color(0xFF4CAF50)
        )
        DataCard(label = "Activity Count", value = state.activityCount?.toString() ?: "--", icon = Icons.AutoMirrored.Filled.DirectionsWalk, color = Color(0xFFFFC107))
        DataCard(
            label = "Steps",
            value = state.steps?.toString() ?: "--",
            icon = Icons.Default.Timeline,
            color = Color(0xFF8BC34A),
            action = {
                MeasurementButton(
                    isActive = false,
                    enabled = state.isConnected,
                    onClick = { viewModel.queryCurrentSteps() }
                )
            }
        )
        
        val total = state.sleepMinutes ?: 0
        DataCard(
            label = "Sleep", 
            value = if (total > 0) "${total / 60}h ${total % 60}m" else "--", 
            icon = Icons.Default.NightsStay, 
            color = Color(0xFF9C27B0),
            supportingText = buildSleepSummary(state),
            action = {
                val deep = (state.deepSleepMinutes ?: 0).toFloat()
                val light = (state.lightSleepMinutes ?: 0).toFloat()
                val totalF = total.toFloat().coerceAtLeast(1f)
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.width(60.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f))) {
                        if (totalF > 0) {
                            if (deep > 0) Box(modifier = Modifier.fillMaxHeight().weight(deep / totalF).background(Color(0xFF311B92)))
                            if (light > 0) Box(modifier = Modifier.fillMaxHeight().weight(light / totalF).background(Color(0xFF7E57C2)))
                            val awake = (totalF - deep - light).coerceAtLeast(0f)
                            if (awake > 0) {
                                Box(modifier = Modifier.fillMaxHeight().weight(awake / totalF).background(Color(0xFFFFEB3B)))
                            }
                        }
                    }
                    MeasurementButton(
                        isActive = false,
                        enabled = state.isConnected,
                        onClick = { viewModel.querySleepBoundaries() }
                    )
                }
            }
        )
        if (state.sleepSegments.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.sleepSegments.forEach { segment ->
                    Text(
                        text = "${formatSleepTime(segment.startMinutes)} - ${formatSleepTime(segment.endMinutes)} ${segment.label}${if (segment.hasInternalMarkers) " *" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
                Text(
                    text = "* contains internal watch boundary markers",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        DataCard(
            label = "Heart Rate",
            value = state.heartRate?.let { "$it bpm" } ?: "--",
            icon = Icons.Default.Favorite,
            color = Color(0xFFE91E63),
            action = {
                MeasurementButton(
                    isActive = state.activeMeasurement == "Heart Rate",
                    enabled = state.isConnected,
                    onClick = {
                        if (state.activeMeasurement == "Heart Rate") viewModel.stopMeasurement()
                        else viewModel.startMeasurement("Heart Rate")
                    }
                )
            }
        )
        
        DataCard(
            label = "SpO2",
            value = state.spo2?.let { "$it%" } ?: "--",
            icon = Icons.Default.Bloodtype,
            color = Color(0xFF00BCD4),
            action = {
                MeasurementButton(
                    isActive = state.activeMeasurement == "SpO2",
                    enabled = state.isConnected,
                    onClick = {
                        if (state.activeMeasurement == "SpO2") viewModel.stopMeasurement()
                        else viewModel.startMeasurement("SpO2")
                    }
                )
            }
        )
        
        DataCard(
            label = "Blood Pressure",
            value = if (state.systolic != null && state.diastolic != null) "${state.systolic}/${state.diastolic}" else "--",
            icon = Icons.Default.Speed,
            color = Color(0xFFFF5722),
            action = {
                MeasurementButton(
                    isActive = state.activeMeasurement == "Blood Pressure",
                    enabled = state.isConnected,
                    onClick = {
                        if (state.activeMeasurement == "Blood Pressure") viewModel.stopMeasurement()
                        else viewModel.startMeasurement("Blood Pressure")
                    }
                )
            }
        )

        
        DataCard(label = "Distance", value = state.distance?.let { "$it m" } ?: "--", icon = Icons.Default.Straighten, color = Color(0xFF2196F3))
        val adjustedCalories = state.calories?.let { (it - state.calorieBaseline).coerceAtLeast(0) }
        DataCard(label = "Calories", value = adjustedCalories?.let { "$it kcal" } ?: "--", icon = Icons.Default.LocalFireDepartment, color = Color(0xFFFF9800))
    }
}

private fun buildSleepSummary(state: WatchState): String {
    val deep = state.deepSleepMinutes ?: 0
    val light = state.lightSleepMinutes ?: 0
    return "Syva: ${deep}m, Kevyt/REM: ${light}m"
}

private fun formatSleepTime(minutes: Int): String {
    return "%02d:%02d".format((minutes / 60) % 24, minutes % 60)
}

private fun relativeTimeLabel(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ago"
    }
}

@Composable
fun MeasurementButton(isActive: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp).background(
            if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
            },
            RoundedCornerShape(8.dp)
        )
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
