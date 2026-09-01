package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.health.calculateHealthInsights

@Composable
fun HealthInsightsCard(state: WatchState) {
    val insights = remember(state) { calculateHealthInsights(state) }
    SleekCard(borderColor = Color(0xFF00BCD4)) {
        Text("Recovery Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(insights.headline, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InsightValue("Score", "${insights.recoveryScore}/100")
            InsightValue("Sleep", "${insights.latestSleepMinutes / 60}h ${insights.latestSleepMinutes % 60}m")
            InsightValue("Active days", insights.activeDaysThisWeek.toString())
        }
        insights.averageHeartRate?.let {
            Spacer(Modifier.height(8.dp))
            Text("Recent average HR: $it bpm", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun InsightValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00BCD4))
    }
}
