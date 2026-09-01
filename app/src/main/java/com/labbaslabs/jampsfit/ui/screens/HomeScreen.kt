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
import com.labbaslabs.jampsfit.GaugeSetting
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.R
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.food.calculateBasalCalories
import com.labbaslabs.jampsfit.food.calculateBasalCaloriesSoFar
import com.labbaslabs.jampsfit.ui.components.DataCard
import com.labbaslabs.jampsfit.ui.components.DancingEventControlCard
import com.labbaslabs.jampsfit.ui.components.CurrentWatchExerciseCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border

@Composable
fun HomeScreen(state: WatchState, scrollState: ScrollState = rememberScrollState()) {
    val viewModel = LocalMainViewModel.current
    var selectedGauge by remember { mutableStateOf<GaugeMetric?>(null) }
    fun gauge(setting: GaugeSetting): Int = state.gaugeSettings[setting] ?: setting.defaultValue

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
        state.connectionDetail?.let { detail ->
            Text(
                text = if (state.reconnectAttempt > 0) "$detail (attempt ${state.reconnectAttempt})" else detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        if (state.deviceName != null) {
            Text(text = state.deviceName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        state.lastWatchSeenTime?.let {
            Text(text = "Last seen ${relativeTimeLabel(it)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        if (state.pendingHealthSyncCount > 0 || state.lastHealthSyncError != null || state.lastHealthSyncTime != null) {
            HealthSyncStatusCard(state = state, onRetry = { viewModel.retryPendingHealthSync() })
        }

        Spacer(modifier = Modifier.height(8.dp))

        DancingEventControlCard(
            state = state,
            onStart = { viewModel.startDancingEvent() },
            onStop = { viewModel.stopActiveEvent() }
        )

        CurrentWatchExerciseCard(state)

        val adjustedCalories = state.calories?.let { (it - state.calorieBaseline).coerceAtLeast(0) }
        val basalCaloriesSoFar = calculateBasalCaloriesSoFar(calculateBasalCalories(state))
        val totalCalories = adjustedCalories?.let { it + basalCaloriesSoFar }
        MetricGaugeGrid(
            metrics = listOf(
                GaugeMetric("Battery", state.battery, "%", gauge(GaugeSetting.BATTERY), 100, Icons.Default.BatteryChargingFull, Color(0xFF4CAF50), state.batteryEstimation, lowReference = gauge(GaugeSetting.BATTERY_LOW), defaultLowReference = 0, onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.BATTERY, it) }, onLowReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.BATTERY_LOW, it) }),
                GaugeMetric("Activity", state.activityCount, "", gauge(GaugeSetting.ACTIVITY), 100, Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFFFFC107), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.ACTIVITY, it) }),
                GaugeMetric("Steps", state.steps, "", gauge(GaugeSetting.STEPS), 10_000, Icons.Default.Timeline, Color(0xFF8BC34A), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.STEPS, it) }, action = {
                    MeasurementButton(isActive = false, enabled = state.isConnected, onClick = { viewModel.queryCurrentSteps() })
                }),
                GaugeMetric("Heart Rate", state.heartRate, " bpm", gauge(GaugeSetting.HEART_RATE), 75, Icons.Default.Favorite, Color(0xFFE91E63), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.HEART_RATE, it) }, action = {
                    MeasurementButton(
                        isActive = state.activeMeasurement == "Heart Rate",
                        enabled = state.isConnected,
                        onClick = {
                            if (state.activeMeasurement == "Heart Rate") viewModel.stopMeasurement()
                            else viewModel.startMeasurement("Heart Rate")
                        }
                    )
                }),
                GaugeMetric("SpO2", state.spo2, "%", gauge(GaugeSetting.SPO2), 98, Icons.Default.Bloodtype, Color(0xFF00BCD4), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.SPO2, it) }, action = {
                    MeasurementButton(
                        isActive = state.activeMeasurement == "SpO2",
                        enabled = state.isConnected,
                        onClick = {
                            if (state.activeMeasurement == "SpO2") viewModel.stopMeasurement()
                            else viewModel.startMeasurement("SpO2")
                        }
                    )
                }),
                GaugeMetric("Blood Pressure", state.systolic, " sys", gauge(GaugeSetting.BLOOD_PRESSURE_SYSTOLIC), 120, Icons.Default.Speed, Color(0xFFFF5722), secondaryValue = state.diastolic, secondaryUnit = " dia", secondaryReference = gauge(GaugeSetting.BLOOD_PRESSURE_DIASTOLIC), defaultSecondaryReference = 80, secondaryColor = Color(0xFF03A9F4), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.BLOOD_PRESSURE_SYSTOLIC, it) }, onSecondaryReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.BLOOD_PRESSURE_DIASTOLIC, it) }, action = {
                    MeasurementButton(
                        isActive = state.activeMeasurement == "Blood Pressure",
                        enabled = state.isConnected,
                        onClick = {
                            if (state.activeMeasurement == "Blood Pressure") viewModel.stopMeasurement()
                            else viewModel.startMeasurement("Blood Pressure")
                        }
                    )
                }),
                GaugeMetric("Distance", state.distance, " m", gauge(GaugeSetting.DISTANCE), 5_000, Icons.Default.Straighten, Color(0xFF2196F3), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.DISTANCE, it) }),
                GaugeMetric("Calories Burned", adjustedCalories, " kcal", gauge(GaugeSetting.CALORIES_BURNED), 500, Icons.Default.LocalFireDepartment, Color(0xFFFF9800), onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.CALORIES_BURNED, it) }),
                GaugeMetric("Total Calories", totalCalories, " kcal", gauge(GaugeSetting.TOTAL_CALORIES), 2500, Icons.Default.LocalFireDepartment, Color(0xFF00BCD4), supportingText = "Base + burned", onReferenceChange = { viewModel.updateGaugeReference(GaugeSetting.TOTAL_CALORIES, it) })
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

@Composable
private fun HealthSyncStatusCard(state: WatchState, onRetry: () -> Unit) {
    val hasError = state.lastHealthSyncError != null
    val color = when {
        hasError -> Color(0xFFFF7043)
        state.pendingHealthSyncCount > 0 -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }
    DataCard(
        label = "Health sync",
        value = when {
            hasError -> "Retry needed"
            state.pendingHealthSyncCount > 0 -> "${state.pendingHealthSyncCount} pending"
            else -> "Up to date"
        },
        supportingText = when {
            hasError -> state.lastHealthSyncError!!.take(64)
            state.lastHealthSyncTime != null -> "Last saved ${relativeTimeLabel(state.lastHealthSyncTime)}"
            else -> "Waiting for watch data"
        },
        icon = if (hasError) Icons.Default.Warning else Icons.Default.Sync,
        color = color,
        action = if (hasError || state.pendingHealthSyncCount > 0) {
            {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry health sync", tint = color)
                }
            }
        } else null
    )
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
    val lowReference: Int = 0,
    val defaultLowReference: Int = 0,
    val secondaryValue: Int? = null,
    val secondaryUnit: String = "",
    val secondaryReference: Int? = null,
    val defaultSecondaryReference: Int? = null,
    val secondaryColor: Color = Color(0xFF03A9F4),
    val onReferenceChange: (Int) -> Unit = {},
    val onLowReferenceChange: ((Int) -> Unit)? = null,
    val onSecondaryReferenceChange: ((Int) -> Unit)? = null,
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
    val range = (metric.reference - metric.lowReference).coerceAtLeast(1)
    val ratio = ((metric.value ?: metric.lowReference) - metric.lowReference).toFloat() / range
    val progress = ratio.coerceIn(0f, 1f)
    val overflowProgress = (ratio - 1f).coerceIn(0f, 1f)
    val secondaryRatio = metric.secondaryReference?.let { reference ->
        (metric.secondaryValue ?: 0).toFloat() / reference.coerceAtLeast(1)
    } ?: 0f
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
                if (overflowProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { overflowProgress },
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        color = Color(0xFF9C27B0),
                        strokeWidth = 5.dp,
                        trackColor = Color.Transparent
                    )
                }
                if (metric.secondaryReference != null) {
                    CircularProgressIndicator(
                        progress = { secondaryRatio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize().padding(13.dp),
                        color = metric.secondaryColor,
                        strokeWidth = 5.dp,
                        trackColor = Color.Transparent
                    )
                    if (secondaryRatio > 1f) {
                        CircularProgressIndicator(
                            progress = { (secondaryRatio - 1f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize().padding(19.dp),
                            color = Color(0xFF9C27B0),
                            strokeWidth = 4.dp,
                            trackColor = Color.Transparent
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(referenceLabel(metric), style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                    Text(
                        currentLabel(metric),
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
    var lowDraft by remember { mutableStateOf(metric.lowReference.toString()) }
    var secondaryDraft by remember { mutableStateOf(metric.secondaryReference?.toString() ?: "") }
    LaunchedEffect(metric.label, metric.reference) {
        draft = metric.reference.toString()
        lowDraft = metric.lowReference.toString()
        secondaryDraft = metric.secondaryReference?.toString() ?: ""
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
                metric.onLowReferenceChange?.let {
                    OutlinedTextField(
                        value = lowDraft,
                        onValueChange = { lowDraft = it.filter(Char::isDigit).take(6) },
                        label = { Text("Lowest value") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                metric.onSecondaryReferenceChange?.let {
                    OutlinedTextField(
                        value = secondaryDraft,
                        onValueChange = { secondaryDraft = it.filter(Char::isDigit).take(6) },
                        label = { Text("Secondary reference${if (metric.secondaryUnit.isBlank()) "" else " (${metric.secondaryUnit.trim()})"}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Default: ${metric.defaultReference}${metric.unit}" + (metric.defaultSecondaryReference?.let { ", secondary $it${metric.secondaryUnit}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    draft.toIntOrNull()?.takeIf { it > 0 }?.let(metric.onReferenceChange)
                    lowDraft.toIntOrNull()?.let { value ->
                        metric.onLowReferenceChange?.invoke(value.coerceAtMost((draft.toIntOrNull() ?: metric.reference) - 1).coerceAtLeast(0))
                    }
                    secondaryDraft.toIntOrNull()?.takeIf { it > 0 }?.let { value ->
                        metric.onSecondaryReferenceChange?.invoke(value)
                    }
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    metric.onReferenceChange(metric.defaultReference)
                    metric.onLowReferenceChange?.invoke(metric.defaultLowReference)
                    metric.defaultSecondaryReference?.let { metric.onSecondaryReferenceChange?.invoke(it) }
                    onDismiss()
                }) {
                    Text("Use Default")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun referenceLabel(metric: GaugeMetric): String {
    return if (metric.secondaryReference != null) {
        "${metric.reference}/${metric.secondaryReference}"
    } else if (metric.lowReference > 0) {
        "${metric.lowReference}-${metric.reference}${metric.unit}"
    } else {
        "${metric.reference}${metric.unit}"
    }
}

private fun currentLabel(metric: GaugeMetric): String {
    return if (metric.secondaryValue != null) {
        "${metric.value ?: "--"}/${metric.secondaryValue}"
    } else {
        metric.value?.let { "$it${metric.unit}" } ?: "--"
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
