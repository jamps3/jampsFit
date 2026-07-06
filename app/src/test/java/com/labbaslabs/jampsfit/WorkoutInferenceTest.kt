package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.HistoryPoint
import com.labbaslabs.jampsfit.workout.estimateActiveCalories
import com.labbaslabs.jampsfit.workout.inferLatestWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutInferenceTest {
    @Test
    fun infersWorkoutFromContiguousHeartRateStream() {
        val points = (0..228).map { index ->
            HistoryPoint(value = 94, timestamp = 1_000L + index * 1_000L)
        }

        val workout = inferLatestWorkout(points, weightKg = 83f)

        assertNotNull(workout)
        checkNotNull(workout)
        assertEquals(228, workout.durationSeconds)
        assertEquals(94, workout.averageBpm)
        assertEquals(94, workout.minBpm)
        assertEquals(94, workout.maxBpm)
        assertEquals(14, workout.estimatedCalories)
    }

    @Test
    fun choosesLatestSessionAfterGap() {
        val first = (0..80).map { HistoryPoint(value = 80, timestamp = it * 1_000L) }
        val second = (0..90).map { HistoryPoint(value = 100, timestamp = 200_000L + it * 1_000L) }

        val workout = inferLatestWorkout(first + second, weightKg = 83f)

        assertNotNull(workout)
        checkNotNull(workout)
        assertEquals(90, workout.durationSeconds)
        assertEquals(100, workout.averageBpm)
    }

    @Test
    fun ignoresShortHeartRateBursts() {
        val points = (0..10).map { index ->
            HistoryPoint(value = 94, timestamp = index * 1_000L)
        }

        assertNull(inferLatestWorkout(points, weightKg = 83f))
    }

    @Test
    fun calorieEstimateUsesActiveBpmAboveResting() {
        assertEquals(14, estimateActiveCalories(averageBpm = 94, durationSeconds = 228, weightKg = 83f))
        assertEquals(0, estimateActiveCalories(averageBpm = 58, durationSeconds = 228, weightKg = 83f))
    }
}
