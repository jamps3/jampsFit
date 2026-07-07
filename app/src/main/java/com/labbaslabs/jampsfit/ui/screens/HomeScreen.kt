package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    var batteryReference by rememberSaveable { mutableStateOf("100") }
    var activityReference by rememberSaveable { mutableStateOf("100") }
    var stepsReference by rememberSaveable { mutableStateOf("10000") }
    var heartRateReference by rememberSaveable { mutableStateOf("75") }
    var spo2Reference by rememberSaveable { mutableStateOf("98") }
    var bloodPressureReference by rememberSaveable { mutableStateOf("120") }
    var distanceReference by rememberSaveable { mutableStateOf("5000") }
    var caloriesReference by rememberSaveable { mutableStateOf("500") }
    var selectedGauge by remember { mutableStateOf<GaugeMetric?>(null) }

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

        val adjustedCalories = state.calories?.let { (it - state.calorieBaseline).coerceAtLeast(0) }
        MetricGaugeGrid(
            metrics = listOf(
                GaugeMetric("Battery", state.battery, "%", gaugeReference(batteryReference, 100), 100, Icons.Default.BatteryChargingFull, Color(0xFF4CAF50), state.batteryEstimation, onReferenceChange = { batteryReference = it.toString() }),
                GaugeMetric("Activity", state.activityCount, "", gaugeReference(activityReference, 100), 100, Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFFFFC107), onReferenceChange = { activityReference = it.toString() }),
                GaugeMetric("Steps", state.steps, "", gaugeReference(stepsReference, 10_000), 10_000, Icons.Default.Timeline, Color(0xFF8BC34A), onReferenceChange = { stepsReference = it.toString() }, action = {
                    MeasurementButton(isActive = false, enabled = state.isConnected, onClick = { viewModel.queryCurrentSteps() })
                }),
                GaugeMetric("Heart Rate", state.heartRate, " bpm", gaugeReference(heartRateReference, 75), 75, Icons.Default.Favorite, Color(0xFFE91E63), onReferenceChange = { heartRateReference = it.toString() }, action = {
                    MeasurementButton(
                        isActive = state.activeMeasurement == "Heart Rate",
                        enabled = state.isConnected,
                        onClick = {
                            if (state.activeMeasurement == "Heart Rate") viewModel.stopMeasurement()
                            else viewModel.startMeasurement("Heart Rate")
                        }
                    )
                }),
                GaugeMetric("SpO2", state.spo2, "%", gaugeReference(spo2Reference, 98), 98, Icons.Default.Bloodtype, Color(0xFF00BCD4), onReferenceChange = { spo2Reference = it.toString() }, action = {
                    MeasurementButton(
                        isActive = state.activeMeasurement == "SpO2",
                        enabled = state.isConnected,
                        onClick = {
                            if (state.activeMeasurement == "SpO2") viewModel.stopMeasurement()
                            else viewModel.startMeasurement("SpO2")
                        }
                    )
                }),
                GaugeMetric("Blood Pressure", state.systolic, state.diastolic?.let { "/$it" } ?: "", gaugeReference(bloodPressureReference, 120), 120, Icons.Default.Speed, Color(0xFFFF5722), onReferenceChange = { bloodPressureReference = it.toString() }, action = {
                    MeasurementButton(
                        isActive = state.activeMeasurement == "Blood Pressure",
                        enabled = state.isConnected,
                        onClick = {
                            if (state.activeMeasurement == "Blood Pressure") viewModel.stopMeasurement()
                            else viewModel.startMeasurement("Blood Pressure")
                        }
                    )
                }),
                GaugeMetric("Distance", state.distance, " m", gaugeReference(distanceReference, 5_000), 5_000, Icons.Default.Straighten, Color(0xFF2196F3), onReferenceChange = { distanceReference = it.toString() }),
                GaugeMetric("Calories", adjustedCalories, " kcal", gaugeReference(caloriesReference, 500), 500, Icons.Default.LocalFireDepartment, Color(0xFFFF9800), onReferenceChange = { caloriesReference = it.toString() })
            ),
            onMetricClick = { selectedGauge = it }
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

    }
    selectedGauge?.let { metric ->
        GaugeSettingsDialog(metric = metric, onDismiss = { selectedGauge = null })
    }
}

private data class GaugeMetric(
    val label: String,
    val value: Int?,
    val unit: String,
    val reference: Int,
    val defaultReference: Int,
    val icon: ImageVector,
    val color: Color,
    val supportingText: String? = null,
    val onReferenceChange: (Int) -> Unit = {},
    val action: @Composable (() -> Unit)? = null
)

@Composable
private fun MetricGaugeGrid(metrics: List<GaugeMetric>, onMetricClick: (GaugeMetric) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { metric ->
                    GaugeMetricCard(metric = metric, onClick = { onMetricClick(metric) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GaugeMetricCard(metric: GaugeMetric, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress = ((metric.value ?: 0).toFloat() / metric.reference.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(
        modifier = modifier.height(168.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(metric.icon, contentDescription = null, tint = metric.color, modifier = Modifier.size(18.dp))
                metric.action?.invoke()
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = metric.color.copy(alpha = 0.18f),
                    strokeWidth = 8.dp,
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = metric.color,
                    strokeWidth = 8.dp,
                    trackColor = Color.Transparent
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${metric.reference}${metric.unit}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                    Text(
                        metric.value?.let { "$it${metric.unit}" } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = metric.color,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(metric.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                metric.supportingText?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun GaugeSettingsDialog(metric: GaugeMetric, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(metric.reference.toString()) }
    LaunchedEffect(metric.label, metric.reference) {
        draft = metric.reference.toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${metric.label} Meter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Center value / reference point", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.filter(Char::isDigit).take(6) },
                    label = { Text("Reference${if (metric.unit.isBlank()) "" else " (${metric.unit.trim()})"}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Default: ${metric.defaultReference}${metric.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    draft.toIntOrNull()?.takeIf { it > 0 }?.let(metric.onReferenceChange)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { metric.onReferenceChange(metric.defaultReference); onDismiss() }) {
                    Text("Use Default")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun gaugeReference(value: String, default: Int): Int {
    return value.toIntOrNull()?.takeIf { it > 0 } ?: default
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
