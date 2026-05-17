package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard

@Composable
fun GraphsScreen(state: WatchState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Live Trends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SleekGraphCard(title = "Battery (%)", data = state.batteryHistory, currentValue = state.battery?.let { "$it%" }, color = Color(0xFF4CAF50))
        SleekGraphCard(title = "Activity Count", data = state.activityHistory, currentValue = state.activityCount?.toString(), color = Color(0xFF8BC34A))
        SleekGraphCard(title = "Distance (m)", data = state.distanceHistory, currentValue = state.distance?.let { "${it}m" }, color = Color(0xFF2196F3))
        SleekGraphCard(title = "Heart Rate (BPM)", data = state.heartRateHistory, currentValue = state.heartRate?.let { "$it bpm" }, color = Color(0xFFE91E63))
        SleekGraphCard(title = "SpO2 (%)", data = state.spo2History, currentValue = state.spo2?.let { "$it%" }, color = Color(0xFF00BCD4))
        
        SleekCard {
            Text(text = "Sleep Distribution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (state.sleepMinutes == null) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Waiting for data...", color = Color.Gray)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
                    val total = state.sleepMinutes.toFloat()
                    val deep = (state.deepSleepMinutes ?: 0).toFloat()
                    val light = (state.lightSleepMinutes ?: 0).toFloat()
                    val awake = (total - deep - light).coerceAtLeast(0f)

                    SleepBar(weight = deep / total, color = Color(0xFF311B92), label = "Deep")
                    SleepBar(weight = light / total, color = Color(0xFF7E57C2), label = "Light")
                    if (awake > 0) {
                        SleepBar(weight = awake / total, color = Color(0xFFFFEB3B), label = "Awake")
                    }
                }
            }
        }
        
        SleekCard {
            Text(text = "Blood Pressure (mmHg)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (state.bpHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Waiting for data...", color = Color.Gray)
                }
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val width = size.width
                    val height = size.height
                    val maxVal = (state.bpHistory.maxOf { it.first }.toFloat()).coerceAtLeast(140f)
                    val minVal = (state.bpHistory.minOf { it.second }.toFloat()).coerceAtMost(60f)
                    val range = (maxVal - minVal).coerceAtLeast(1f)
                    val sysPath = Path()
                    val diaPath = Path()
                    state.bpHistory.forEachIndexed { i, pair ->
                        val x = (i.toFloat() / (state.bpHistory.size - 1).coerceAtLeast(1)) * width
                        val sysY = height - ((pair.first.toFloat() - minVal) / range) * height
                        val diaY = height - ((pair.second.toFloat() - minVal) / range) * height
                        if (i == 0) { sysPath.moveTo(x, sysY); diaPath.moveTo(x, diaY) }
                        else { sysPath.lineTo(x, sysY); diaPath.lineTo(x, diaY) }
                    }
                    drawPath(sysPath, color = Color(0xFFFF5722), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(diaPath, color = Color(0xFF3F51B5), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

@Composable
fun RowScope.SleepBar(weight: Float, color: Color, label: String) {
    if (weight > 0) {
        Column(modifier = Modifier.fillMaxHeight().weight(weight).padding(horizontal = 2.dp)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(color, RoundedCornerShape(4.dp)))
            Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun SleekGraphCard(title: String, data: List<Int>, currentValue: String?, color: Color) {
    SleekCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = currentValue ?: "--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(16.dp))
        val graphData = if (data.isNotEmpty()) data else currentValue?.filter { it.isDigit() }?.toIntOrNull()?.let { listOf(it) }.orEmpty()
        if (graphData.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for data...", color = Color.Gray)
            }
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val width = size.width
                val height = size.height
                val currentMax = (graphData.maxOrNull()?.toFloat() ?: 100f).coerceAtLeast(1f)
                val currentMin = (graphData.minOrNull()?.toFloat() ?: 0f)
                val range = (currentMax - currentMin).coerceAtLeast(1f)
                val path = Path()
                graphData.forEachIndexed { i, value ->
                    val x = if (graphData.size == 1) width / 2f else (i.toFloat() / (graphData.size - 1)) * width
                    val y = height - ((value.toFloat() - currentMin) / range) * height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (graphData.size == 1) {
                    drawCircle(color = color, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f))
                } else {
                    drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent)))
                }
            }
        }
    }
}
