package com.labbaslabs.jampsfit.workout

import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.HistoryPoint
import kotlin.math.roundToInt

private const val SESSION_GAP_MS = 10_000L
private const val MIN_SESSION_DURATION_MS = 60_000L
private const val MIN_SESSION_SAMPLES = 5
private const val RESTING_BPM_BASELINE = 60
private const val ACTIVE_KCAL_DIVISOR = 750f

data class InferredWorkout(
    val startTime: Long,
    val endTime: Long,
    val sampleCount: Int,
    val averageBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val durationSeconds: Int,
    val estimatedCalories: Int
)

fun inferLatestWorkout(state: WatchState): InferredWorkout? {
    return inferLatestWorkout(
        heartRatePoints = state.heartRateHistory,
        weightKg = state.profileWeightKg
    )
}

fun inferLatestWorkout(
    heartRatePoints: List<HistoryPoint>,
    weightKg: Float
): InferredWorkout? {
    val sessions = heartRatePoints
        .filter { it.value > 0 }
        .sortedBy { it.timestamp }
        .fold(mutableListOf<MutableList<HistoryPoint>>()) { acc, point ->
            val current = acc.lastOrNull()
            if (current == null || point.timestamp - current.last().timestamp > SESSION_GAP_MS) {
                acc.add(mutableListOf(point))
            } else {
                current.add(point)
            }
            acc
        }

    val latest = sessions.lastOrNull { session ->
        session.size >= MIN_SESSION_SAMPLES && session.last().timestamp - session.first().timestamp >= MIN_SESSION_DURATION_MS
    } ?: return null

    val durationSeconds = ((latest.last().timestamp - latest.first().timestamp) / 1000L).toInt()
    val averageBpm = latest.map { it.value }.average().roundToInt()
    return InferredWorkout(
        startTime = latest.first().timestamp,
        endTime = latest.last().timestamp,
        sampleCount = latest.size,
        averageBpm = averageBpm,
        minBpm = latest.minOf { it.value },
        maxBpm = latest.maxOf { it.value },
        durationSeconds = durationSeconds,
        estimatedCalories = estimateActiveCalories(
            averageBpm = averageBpm,
            durationSeconds = durationSeconds,
            weightKg = weightKg
        )
    )
}

fun estimateActiveCalories(averageBpm: Int, durationSeconds: Int, weightKg: Float): Int {
    val activeBpm = (averageBpm - RESTING_BPM_BASELINE).coerceAtLeast(0)
    val minutes = durationSeconds / 60f
    return ((activeBpm * weightKg * minutes) / ACTIVE_KCAL_DIVISOR).roundToInt().coerceAtLeast(0)
}
