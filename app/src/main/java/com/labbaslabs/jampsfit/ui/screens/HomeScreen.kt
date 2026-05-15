package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.labbaslabs.jampsfit.MainActivity
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.DataCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

@Composable
fun HomeScreen(state: WatchState) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "jampsFit",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = if (state.isConnected) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)),
            modifier = Modifier.border(1.dp, if (state.isConnected) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
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

        Spacer(modifier = Modifier.height(8.dp))

        DataCard(
            label = "Battery",
            value = state.battery?.let { "$it%" } ?: "--",
            supportingText = state.batteryEstimation,
            icon = Icons.Default.BatteryChargingFull,
            color = Color(0xFF4CAF50)
        )
        DataCard(label = "Steps", value = state.steps?.toString() ?: "--", icon = Icons.Default.DirectionsWalk, color = Color(0xFFFFC107))
        
        val total = state.sleepMinutes ?: 0
        DataCard(
            label = "Sleep", 
            value = if (total > 0) "${total / 60}h ${total % 60}m" else "--", 
            icon = Icons.Default.NightsStay, 
            color = Color(0xFF9C27B0),
            supportingText = "Deep: ${state.deepSleepMinutes ?: 0}m, Light: ${state.lightSleepMinutes ?: 0}m",
            action = {
                val deep = (state.deepSleepMinutes ?: 0).toFloat()
                val light = (state.lightSleepMinutes ?: 0).toFloat()
                val totalF = total.toFloat().coerceAtLeast(1f)
                
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
            }
        )

        DataCard(
            label = "Heart Rate",
            value = state.heartRate?.let { "$it bpm" } ?: "--",
            icon = Icons.Default.Favorite,
            color = Color(0xFFE91E63),
            action = {
                MeasurementButton(
                    isActive = state.activeMeasurement == "Heart Rate",
                    onClick = {
                        if (state.activeMeasurement == "Heart Rate") activity?.stopMeasurement()
                        else activity?.startMeasurement("Heart Rate")
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
                    onClick = {
                        if (state.activeMeasurement == "SpO2") activity?.stopMeasurement()
                        else activity?.startMeasurement("SpO2")
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
                    onClick = {
                        if (state.activeMeasurement == "Blood Pressure") activity?.stopMeasurement()
                        else activity?.startMeasurement("Blood Pressure")
                    }
                )
            }
        )
        
        DataCard(label = "Distance", value = state.distance?.let { "$it m" } ?: "--", icon = Icons.Default.Straighten, color = Color(0xFF2196F3))
        DataCard(label = "Calories", value = state.calories?.let { "$it kcal" } ?: "--", icon = Icons.Default.LocalFireDepartment, color = Color(0xFFFF9800))
    }
}

@Composable
fun MeasurementButton(isActive: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp).background(
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
