package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.DataCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

@Composable
fun HomeScreen(state: WatchState) {
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
        DataCard(label = "Heart Rate", value = state.heartRate?.let { "$it bpm" } ?: "--", icon = Icons.Default.Favorite, color = Color(0xFFE91E63))
        DataCard(label = "SpO2", value = state.spo2?.let { "$it%" } ?: "--", icon = Icons.Default.Bloodtype, color = Color(0xFF00BCD4))
        DataCard(label = "Blood Pressure", value = if (state.systolic != null && state.diastolic != null) "${state.systolic}/${state.diastolic}" else "--", icon = Icons.Default.Speed, color = Color(0xFFFF5722))
        DataCard(label = "Distance", value = state.distance?.let { "$it m" } ?: "--", icon = Icons.Default.Straighten, color = Color(0xFF2196F3))
        DataCard(label = "Calories", value = state.calories?.let { "$it kcal" } ?: "--", icon = Icons.Default.LocalFireDepartment, color = Color(0xFFFF9800))
    }
}
