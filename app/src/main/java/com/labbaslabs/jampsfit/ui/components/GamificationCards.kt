package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.gamification.Achievement
import com.labbaslabs.jampsfit.gamification.GoalProgress
import com.labbaslabs.jampsfit.gamification.calculateGamificationSummary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamificationCard(state: WatchState) {
    val summary = remember(state) { calculateGamificationSummary(state) }
    val unlocked = summary.achievements.filter { it.unlocked }.takeLast(4)

    SleekCard(borderColor = Color(0xFFFFC107)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFC107).copy(alpha = 0.14f)) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.padding(8.dp).size(26.dp)
                    )
                }
                Column {
                    Text("Level ${summary.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("${summary.totalXp} XP", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            StreakBadge(summary.currentStreakDays)
        }

        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { summary.levelXp.toFloat() / summary.levelXpTarget },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color(0xFFFFC107),
            trackColor = Color.DarkGray.copy(alpha = 0.7f)
        )
        Text(
            text = "${summary.levelXp}/${summary.levelXpTarget} XP to level ${summary.level + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            summary.goals.forEach { goal ->
                GoalProgressRow(goal = goal, icon = goal.icon(), color = goal.color())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Achievements", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (unlocked.isEmpty()) {
            Text("Waiting for the first unlock", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                unlocked.forEach { achievement ->
                    AchievementChip(achievement)
                }
            }
        }
    }
}

@Composable
private fun StreakBadge(days: Int) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF5722).copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(18.dp))
            Text("${days}d", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF5722))
        }
    }
}

@Composable
private fun GoalProgressRow(goal: GoalProgress, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goal.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(goal.displayValue(), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = color,
                trackColor = Color.DarkGray.copy(alpha = 0.6f)
            )
        }
        if (goal.isComplete) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AchievementChip(achievement: Achievement) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF4CAF50).copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
            Text(achievement.title, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        }
    }
}

private fun GoalProgress.displayValue(): String {
    val valueText = if (label == "Sleep") "${value / 60}h ${value % 60}m" else value.toString()
    val targetText = if (label == "Sleep") "${target / 60}h" else target.toString()
    val suffix = if (unit.isBlank() || label == "Sleep") "" else " $unit"
    return "$valueText / $targetText$suffix"
}

private fun GoalProgress.icon(): ImageVector = when (label) {
    "Steps" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "Calories" -> Icons.Default.LocalFireDepartment
    "Sleep" -> Icons.Default.NightsStay
    else -> Icons.Default.Timeline
}

private fun GoalProgress.color(): Color = when (label) {
    "Steps" -> Color(0xFF8BC34A)
    "Calories" -> Color(0xFFFF9800)
    "Sleep" -> Color(0xFF9C27B0)
    else -> Color(0xFF03A9F4)
}
