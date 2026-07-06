package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.gamification.Achievement
import com.labbaslabs.jampsfit.gamification.calculateGamificationSummary
import kotlinx.coroutines.delay

private val FestivalAchievementTitles = setOf(
    "Wristband On",
    "First Set",
    "Main Stage",
    "Back-to-Back Sets",
    "Two-Day Groove",
    "Four-Day Pass",
    "5k Dancefloor",
    "10k Dancefloor",
    "Marathon Feet",
    "Beat Keeper",
    "Tempo Story",
    "Heat Wave",
    "Data Collector",
    "Recovery Win"
)

@Composable
fun DancingEventControlCard(
    state: WatchState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val activeEvent = state.activeEvent
    val latestDancingEvent = state.recentEvents.firstOrNull { it.type == EVENT_TYPE_DANCING }
    var now by remember(activeEvent?.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(activeEvent?.id) {
        while (activeEvent != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    SleekCard(borderColor = Color(0xFFE91E63)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE91E63).copy(alpha = 0.14f)) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.padding(8.dp).size(26.dp)
                    )
                }
                Column {
                    Text("Dancing Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (activeEvent == null) "Ready" else "Recording",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeEvent == null) Color.Gray else Color(0xFFE91E63)
                    )
                }
            }
            if (activeEvent == null) {
                Button(onClick = onStart) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Start")
                }
            } else {
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Stop")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        val eventForStats = activeEvent ?: latestDancingEvent
        if (eventForStats == null) {
            Text("No dancing events yet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            EventStats(event = eventForStats, now = now)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FestivalProgressCard(state: WatchState) {
    val summary = remember(state) { calculateGamificationSummary(state) }
    val festivalAchievements = summary.achievements.filter { it.title in FestivalAchievementTitles }
    val unlocked = festivalAchievements.filter { it.unlocked }
    val recentCompleted = state.recentEvents
        .filter { it.type == EVENT_TYPE_DANCING && it.endTime != null }
        .take(3)

    SleekCard(borderColor = Color(0xFF03A9F4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF03A9F4).copy(alpha = 0.14f)) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFF03A9F4),
                    modifier = Modifier.padding(8.dp).size(26.dp)
                )
            }
            Column {
                Text("Festival Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${unlocked.size}/${festivalAchievements.size} unlocked", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (recentCompleted.isEmpty()) {
            Text("No completed dancing events", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentCompleted.forEach { EventSummaryRow(it) }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            festivalAchievements.forEach { achievement ->
                FestivalAchievementChip(achievement)
            }
        }
    }
}

@Composable
private fun EventStats(event: EventEntity, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventStat("Time", event.durationLabel(now), Icons.Default.Timer, Color(0xFF03A9F4), Modifier.weight(1f))
            EventStat("Steps", event.stepDelta.toString(), Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF8BC34A), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventStat("BPM", event.averageBpm?.toString() ?: "--", Icons.Default.Favorite, Color(0xFFE91E63), Modifier.weight(1f))
            EventStat("kcal", event.activeCalories.toString(), Icons.Default.LocalFireDepartment, Color(0xFFFF9800), Modifier.weight(1f))
        }
    }
}

@Composable
private fun EventSummaryRow(event: EventEntity) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(event.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(event.durationLabel(event.endTime ?: event.lastUpdatedTime), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text("${event.stepDelta} steps  ${event.activeCalories} kcal", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
        }
    }
}

@Composable
private fun FestivalAchievementChip(achievement: Achievement) {
    val color = if (achievement.unlocked) Color(0xFF4CAF50) else Color.Gray
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = if (achievement.unlocked) 0.12f else 0.08f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (achievement.unlocked) Icons.Default.CheckCircle else Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(achievement.title, style = MaterialTheme.typography.labelSmall, color = if (achievement.unlocked) Color.LightGray else Color.Gray)
        }
    }
}

@Composable
private fun EventStat(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.10f)) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

private fun EventEntity.durationLabel(now: Long): String {
    val seconds = if (isActive) {
        ((now - startTime) / 1000L).coerceAtLeast(durationSeconds.toLong()).toInt()
    } else {
        durationSeconds
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder) else "%d:%02d".format(minutes, remainder)
}
