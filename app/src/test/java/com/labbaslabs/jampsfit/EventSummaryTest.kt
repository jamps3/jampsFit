package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.events.calculateCounterDelta
import com.labbaslabs.jampsfit.events.estimateStepsFromDistance
import com.labbaslabs.jampsfit.events.summarizeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventSummaryTest {
    @Test
    fun summarizesEventFromBaselinesAndHealthRows() {
        val event = EventEntity(
            type = EVENT_TYPE_DANCING,
            startTime = 1_000L,
            endTime = 121_000L,
            startSteps = 100,
            startActivityCount = 10,
            startDistance = 250,
            startCalories = 20
        )
        val entries = (0..120).map { index ->
            HealthEntry(
                timestamp = 1_000L + index * 1_000L,
                steps = 100 + index,
                activityCount = 10 + index,
                distance = 250 + index * 2,
                calories = 20 + index / 6,
                heartRate = 90
            )
        }

        val summary = summarizeEvent(event, entries, endTime = 121_000L, weightKg = 83f)

        assertEquals(120, summary.durationSeconds)
        assertEquals(120, summary.stepDelta)
        assertEquals(120, summary.activityDelta)
        assertEquals(240, summary.distanceDelta)
        assertEquals(20, summary.calorieDelta)
        assertEquals(121, summary.heartRateSamples)
        assertEquals(90, summary.averageBpm)
        assertEquals(90, summary.minBpm)
        assertEquals(90, summary.maxBpm)
        assertEquals(7, summary.estimatedWorkoutCalories)
    }

    @Test
    fun counterDeltaHandlesMidnightReset() {
        assertEquals(100, calculateCounterDelta(values = listOf(50, 100), baseline = 9_900))
    }

    @Test
    fun summaryEstimatesStepsFromDistanceWhenStepCounterIsFlat() {
        val event = EventEntity(
            type = EVENT_TYPE_DANCING,
            startTime = 1_000L,
            endTime = 121_000L,
            startSteps = 2_111,
            startDistance = 1_668,
            startCalories = 131
        )
        val entries = listOf(
            HealthEntry(timestamp = 61_000L, steps = 2_111, distance = 1_678, calories = 132),
            HealthEntry(timestamp = 121_000L, steps = 2_111, distance = 1_687, calories = 133)
        )

        val summary = summarizeEvent(event, entries, endTime = 121_000L, weightKg = 83f)

        assertEquals(19, summary.distanceDelta)
        assertEquals(25, summary.stepDelta)
        assertEquals(2, summary.calorieDelta)
    }

    @Test
    fun distanceStepEstimateUsesConservativeStride() {
        assertEquals(25, estimateStepsFromDistance(19))
        assertEquals(0, estimateStepsFromDistance(0))
    }

    @Test
    fun summaryHandlesMissingMetrics() {
        val summary = summarizeEvent(
            event = EventEntity(type = EVENT_TYPE_DANCING, startTime = 1_000L, endTime = 2_000L),
            healthEntries = emptyList(),
            endTime = 2_000L,
            weightKg = 83f
        )

        assertEquals(1, summary.durationSeconds)
        assertEquals(0, summary.stepDelta)
        assertEquals(0, summary.activeCalories)
        assertEquals(0, summary.heartRateSamples)
        assertNull(summary.averageBpm)
    }

    @Test
    fun activeEventSummaryKeepsOpenEndTime() {
        val summary = summarizeEvent(
            event = EventEntity(type = EVENT_TYPE_DANCING, startTime = 1_000L, startSteps = 10),
            healthEntries = listOf(HealthEntry(timestamp = 61_000L, steps = 40)),
            endTime = 61_000L,
            weightKg = 83f
        )

        assertNull(summary.endTime)
        assertEquals(60, summary.durationSeconds)
        assertEquals(30, summary.stepDelta)
    }
}
