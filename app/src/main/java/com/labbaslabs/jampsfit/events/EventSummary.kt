package com.labbaslabs.jampsfit.events

import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.database.HistoryPoint
import com.labbaslabs.jampsfit.workout.inferLatestWorkout
import kotlin.math.roundToInt

fun summarizeEvent(
    event: EventEntity,
    healthEntries: List<HealthEntry>,
    endTime: Long,
    weightKg: Float
): EventEntity {
    val summaryEnd = event.endTime ?: endTime
    val entries = healthEntries
        .filter { it.timestamp in event.startTime..summaryEnd }
        .sortedBy { it.timestamp }
    val heartRates = entries.mapNotNull { entry -> entry.heartRate?.takeIf { it > 0 } }
    val heartRatePoints = entries.mapNotNull { entry ->
        entry.heartRate?.takeIf { it > 0 }?.let { HistoryPoint(it, entry.timestamp) }
    }
    val inferredWorkout = inferLatestWorkout(heartRatePoints, weightKg)

    return event.copy(
        durationSeconds = ((summaryEnd - event.startTime) / 1000L).coerceAtLeast(0L).toInt(),
        stepDelta = calculateCounterDelta(entries.mapNotNull { it.steps }, event.startSteps),
        activityDelta = calculateCounterDelta(entries.mapNotNull { it.activityCount }, event.startActivityCount),
        distanceDelta = calculateCounterDelta(entries.mapNotNull { it.distance }, event.startDistance),
        calorieDelta = calculateCounterDelta(entries.mapNotNull { it.calories }, event.startCalories),
        heartRateSamples = heartRates.size,
        averageBpm = heartRates.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        minBpm = heartRates.minOrNull(),
        maxBpm = heartRates.maxOrNull(),
        estimatedWorkoutCalories = inferredWorkout?.estimatedCalories ?: 0,
        lastUpdatedTime = summaryEnd
    )
}

fun calculateCounterDelta(values: List<Int>, baseline: Int?): Int {
    val counters = buildList {
        baseline?.takeIf { it >= 0 }?.let { add(it) }
        values.filterTo(this) { it >= 0 }
    }
    if (counters.size < 2) return 0

    var total = 0
    var previous = counters.first()
    counters.drop(1).forEach { current ->
        total += if (current >= previous) current - previous else current
        previous = current
    }
    return total.coerceAtLeast(0)
}
