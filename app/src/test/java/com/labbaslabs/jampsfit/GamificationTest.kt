package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.HealthEntry
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
}
