package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
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
import com.labbaslabs.jampsfit.workout.InferredWorkout
import com.labbaslabs.jampsfit.workout.inferLatestWorkout

@Composable
fun WorkoutSummaryCard(state: WatchState) {
    val workout = remember(state.heartRateHistory, state.profileWeightKg) { inferLatestWorkout(state) }

    SleekCard(borderColor = Color(0xFFE91E63)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE91E63).copy(alpha = 0.14f)) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.padding(8.dp).size(26.dp)
                    )
                }
                Column {
                    Text("Inferred Workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("From continuous heart-rate stream", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (workout == null) {
            Text("Waiting for a continuous workout heart-rate stream", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            WorkoutStats(workout)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${workout.sampleCount} BPM samples. Calories are estimated until workout summary packets are decoded.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun CurrentWatchExerciseCard(state: WatchState) {
    val workout = remember(state.heartRateHistory, state.profileWeightKg) { inferLatestWorkout(state) }
    val activeWorkout = workout?.takeIf { state.isConnected && System.currentTimeMillis() - it.endTime <= 90_000L }

    SleekCard(borderColor = Color(0xFFFF9800)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF9800).copy(alpha = 0.14f)) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.padding(8.dp).size(26.dp)
                    )
                }
                Column {
                    Text("Current Watch Exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (activeWorkout == null) "Waiting for active heart-rate stream" else "Recording from watch HR",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeWorkout == null) Color.Gray else Color(0xFFFF9800)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (activeWorkout == null) {
            Text("Start an exercise on the watch to see live duration, BPM, and estimated calories here.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            WorkoutStats(activeWorkout)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${activeWorkout.sampleCount} live BPM samples. Watch exercise packets will be saved to festival events once decoded.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun WorkoutStats(workout: InferredWorkout) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WorkoutStat("Duration", workout.durationLabel(), Icons.Default.Timer, Color(0xFF03A9F4), Modifier.weight(1f))
            WorkoutStat("Avg BPM", workout.averageBpm.toString(), Icons.Default.Favorite, Color(0xFFE91E63), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WorkoutStat("Est. kcal", workout.estimatedCalories.toString(), Icons.Default.LocalFireDepartment, Color(0xFFFF9800), Modifier.weight(1f))
            WorkoutStat("Range", "${workout.minBpm}-${workout.maxBpm}", Icons.Default.Whatshot, Color(0xFFFFC107), Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorkoutStat(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
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

private fun InferredWorkout.durationLabel(): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
