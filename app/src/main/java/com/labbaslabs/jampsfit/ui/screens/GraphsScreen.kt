package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.Canvas
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
    val hrHistory = remember { mutableStateListOf<Int>() }
    val spo2History = remember { mutableStateListOf<Int>() }
    val bpHistory = remember { mutableStateListOf<Pair<Int, Int>>() }
    val stepsHistory = remember { mutableStateListOf<Int>() }
    val distanceHistory = remember { mutableStateListOf<Int>() }
    val batteryHistory = remember { mutableStateListOf<Int>() }

    LaunchedEffect(state.battery) { state.battery?.let { batteryHistory.add(it); if (batteryHistory.size > 50) batteryHistory.removeAt(0) } }
    LaunchedEffect(state.heartRate) { state.heartRate?.let { hrHistory.add(it); if (hrHistory.size > 20) hrHistory.removeAt(0) } }
    LaunchedEffect(state.spo2) { state.spo2?.let { spo2History.add(it); if (spo2History.size > 20) spo2History.removeAt(0) } }
    LaunchedEffect(state.systolic, state.diastolic) {
        if (state.systolic != null && state.diastolic != null) {
            bpHistory.add(Pair(state.systolic, state.diastolic))
            if (bpHistory.size > 20) bpHistory.removeAt(0)
        }
    }
    LaunchedEffect(state.steps) { state.steps?.let { stepsHistory.add(it); if (stepsHistory.size > 20) stepsHistory.removeAt(0) } }
    LaunchedEffect(state.distance) { state.distance?.let { distanceHistory.add(it); if (distanceHistory.size > 20) distanceHistory.removeAt(0) } }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Live Trends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SleekGraphCard(title = "Battery (%)", data = batteryHistory, color = Color(0xFF4CAF50))
        SleekGraphCard(title = "Steps (Count)", data = stepsHistory, color = Color(0xFF8BC34A))
        SleekGraphCard(title = "Distance (m)", data = distanceHistory, color = Color(0xFF2196F3))
        SleekGraphCard(title = "Heart Rate (BPM)", data = hrHistory, color = Color(0xFFE91E63))
        SleekGraphCard(title = "SpO2 (%)", data = spo2History, color = Color(0xFF00BCD4))
        
        SleekCard {
            Text(text = "Blood Pressure (mmHg)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (bpHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Waiting for data...", color = Color.Gray)
                }
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val width = size.width
                    val height = size.height
                    val maxVal = (bpHistory.maxOf { it.first }.toFloat()).coerceAtLeast(140f)
                    val minVal = (bpHistory.minOf { it.second }.toFloat()).coerceAtMost(60f)
                    val range = (maxVal - minVal).coerceAtLeast(1f)
                    val sysPath = Path()
                    val diaPath = Path()
                    bpHistory.forEachIndexed { i, pair ->
                        val x = (i.toFloat() / (bpHistory.size - 1).coerceAtLeast(1)) * width
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
fun SleekGraphCard(title: String, data: List<Int>, color: Color) {
    SleekCard {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (data.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for data...", color = Color.Gray)
            }
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val width = size.width
                val height = size.height
                val currentMax = (data.maxOrNull()?.toFloat() ?: 100f).coerceAtLeast(1f)
                val currentMin = (data.minOrNull()?.toFloat() ?: 0f)
                val range = (currentMax - currentMin).coerceAtLeast(1f)
                val path = Path()
                data.forEachIndexed { i, value ->
                    val x = (i.toFloat() / (data.size - 1).coerceAtLeast(1)) * width
                    val y = height - ((value.toFloat() - currentMin) / range) * height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Subtle area fill
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
