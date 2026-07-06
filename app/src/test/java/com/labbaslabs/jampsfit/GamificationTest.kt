package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.gamification.DEFAULT_STEP_GOAL
import com.labbaslabs.jampsfit.gamification.XP_PER_LEVEL
import com.labbaslabs.jampsfit.gamification.calculateDailyXp
import com.labbaslabs.jampsfit.gamification.calculateGamificationSummary
import com.labbaslabs.jampsfit.gamification.calculateStepGoalStreak
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationTest {
    @Test
    fun dailyXpAppliesCapsAndBonuses() {
        val xp = calculateDailyXp(
            steps = 20_000,
            stepGoal = DEFAULT_STEP_GOAL,
            calories = 500,
            sleepMinutes = 420,
            hasHeartRate = true
        )

        assertEquals(190, xp)
    }

    @Test
    fun levelMathUsesCurrentLevelProgress() {
        val summary = calculateGamificationSummary(
            WatchState(
                dailyStats = listOf(
                    entry(daysAgo = 0, steps = 10_000, calories = 500, sleepMinutes = 420, heartRate = 72),
                    entry(daysAgo = 1, steps = 10_000, calories = 500, sleepMinutes = 420, heartRate = 70)
                )
            )
        )

        assertEquals(2, summary.level)
        assertEquals(340, summary.totalXp)
        assertEquals(340 % XP_PER_LEVEL, summary.levelXp)
    }

    @Test
    fun goalProgressIsClamped() {
        val summary = calculateGamificationSummary(WatchState(steps = 12_500))
        val steps = summary.goals.first { it.label == "Steps" }

        assertEquals(1f, steps.progress)
        assertTrue(steps.isComplete)
    }

    @Test
    fun streakCountsConsecutiveDaysFromToday() {
        val streak = calculateStepGoalStreak(
            listOf(
                entry(daysAgo = 0, steps = 10_000),
                entry(daysAgo = 1, steps = 11_000),
                entry(daysAgo = 2, steps = 12_000),
                entry(daysAgo = 3, steps = 4_000),
                entry(daysAgo = 4, steps = 10_000)
            ),
            DEFAULT_STEP_GOAL
        )

        assertEquals(3, streak)
    }

    @Test
    fun achievementsUnlockDeterministically() {
        val summary = calculateGamificationSummary(
            WatchState(
                isConnected = true,
                steps = 15_000,
                calories = 500,
                sleepMinutes = 420,
                heartRate = 72,
                dailyStats = (0..6).map { entry(daysAgo = it, steps = 10_000) }
            )
        )

        val unlocked = summary.achievements.filter { it.unlocked }.map { it.title }
        assertTrue("First Sync" in unlocked)
        assertTrue("10k Steps" in unlocked)
        assertTrue("7-Day Streak" in unlocked)
        assertTrue("Personal Best Steps" in unlocked)
    }

    @Test
    fun festivalAchievementsUnlockFromDancingEvents() {
        val summary = calculateGamificationSummary(
            WatchState(
                recentEvents = listOf(
                    dancingEvent(daysAgo = 3, durationSeconds = 3_600, steps = 6_000, calories = 100, heartRateSamples = 30, minBpm = 80, maxBpm = 112),
                    dancingEvent(daysAgo = 2, durationSeconds = 900, steps = 5_000, calories = 50, heartRateSamples = 10, minBpm = 88, maxBpm = 100),
                    dancingEvent(daysAgo = 1, durationSeconds = 900, steps = 5_000, calories = 50),
                    dancingEvent(daysAgo = 0, durationSeconds = 900, steps = 5_000, calories = 50),
                    dancingEvent(daysAgo = 0, durationSeconds = 900, steps = 500, calories = 0)
                ),
                dailyStats = listOf(entry(daysAgo = 2, steps = 0, sleepMinutes = 360))
            )
        )

        val unlocked = summary.achievements.filter { it.unlocked }.map { it.title }
        assertTrue("Wristband On" in unlocked)
        assertTrue("First Set" in unlocked)
        assertTrue("Main Stage" in unlocked)
        assertTrue("Back-to-Back Sets" in unlocked)
        assertTrue("Two-Day Groove" in unlocked)
        assertTrue("Four-Day Pass" in unlocked)
        assertTrue("5k Dancefloor" in unlocked)
        assertTrue("10k Dancefloor" in unlocked)
        assertTrue("Marathon Feet" in unlocked)
        assertTrue("Beat Keeper" in unlocked)
        assertTrue("Tempo Story" in unlocked)
        assertTrue("Heat Wave" in unlocked)
        assertTrue("Data Collector" in unlocked)
        assertTrue("Recovery Win" in unlocked)
    }

    @Test
    fun emptyStateHasNoUnlockedAchievementsOrProgress() {
        val summary = calculateGamificationSummary(WatchState())

        assertEquals(1, summary.level)
        assertEquals(0, summary.totalXp)
        assertEquals(0, summary.currentStreakDays)
        assertFalse(summary.achievements.any { it.unlocked })
    }

    private fun entry(
        daysAgo: Int,
        steps: Int,
        calories: Int = 0,
        sleepMinutes: Int = 0,
        heartRate: Int = 0
    ): HealthEntry {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        return HealthEntry(
            timestamp = calendar.timeInMillis,
            steps = steps,
            calories = calories,
            sleepMinutes = sleepMinutes,
            heartRate = heartRate
        )
    }

    private fun dancingEvent(
        daysAgo: Int,
        durationSeconds: Int,
        steps: Int,
        calories: Int,
        heartRateSamples: Int = 0,
        minBpm: Int? = null,
        maxBpm: Int? = null
    ): EventEntity {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val startTime = calendar.timeInMillis
        return EventEntity(
            type = EVENT_TYPE_DANCING,
            name = "Dancing Event",
            startTime = startTime,
            endTime = startTime + durationSeconds * 1_000L,
            durationSeconds = durationSeconds,
            stepDelta = steps,
            calorieDelta = calories,
            heartRateSamples = heartRateSamples,
            averageBpm = minBpm,
            minBpm = minBpm,
            maxBpm = maxBpm,
            lastUpdatedTime = startTime + durationSeconds * 1_000L
        )
    }
}
