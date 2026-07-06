package com.labbaslabs.jampsfit.gamification

import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.HealthEntry
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

const val DEFAULT_STEP_GOAL = 10_000
const val DEFAULT_CALORIE_GOAL = 500
const val DEFAULT_SLEEP_GOAL_MINUTES = 7 * 60
const val DEFAULT_ACTIVITY_GOAL = 100
const val XP_PER_LEVEL = 250

data class GamificationSummary(
    val level: Int,
    val totalXp: Int,
    val levelXp: Int,
    val levelXpTarget: Int,
    val currentStreakDays: Int,
    val goals: List<GoalProgress>,
    val achievements: List<Achievement>
)

data class GoalProgress(
    val label: String,
    val value: Int,
    val target: Int,
    val unit: String
) {
    val progress: Float = if (target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)
    val isComplete: Boolean = value >= target
}

data class Achievement(
    val title: String,
    val detail: String,
    val unlocked: Boolean
)

fun calculateGamificationSummary(state: WatchState): GamificationSummary {
    val stepGoal = state.stepGoalSetting?.takeIf { it > 0 } ?: DEFAULT_STEP_GOAL
    val todaySteps = state.steps ?: state.stepsHistory.maxOfOrNull { it.value } ?: 0
    val todayCalories = state.calories ?: state.caloriesHistory.maxOfOrNull { it.value } ?: 0
    val todaySleep = state.sleepMinutes ?: state.dailyStats.maxOfOrNull { it.sleepMinutes ?: 0 } ?: 0
    val todayActivity = state.activityCount ?: state.activityHistory.maxOfOrNull { it.value } ?: 0
    val hasHeartRate = state.heartRate != null || state.heartRateHistory.isNotEmpty() || state.dailyStats.any { (it.heartRate ?: 0) > 0 }

    val dailyXp = calculateDailyXp(
        steps = todaySteps,
        stepGoal = stepGoal,
        calories = todayCalories,
        sleepMinutes = todaySleep,
        hasHeartRate = hasHeartRate
    )
    val historyXp = state.dailyStats.sumOf { entry ->
        calculateDailyXp(
            steps = entry.steps ?: 0,
            stepGoal = stepGoal,
            calories = entry.calories ?: 0,
            sleepMinutes = entry.sleepMinutes ?: 0,
            hasHeartRate = (entry.heartRate ?: 0) > 0
        )
    }
    val totalXp = max(dailyXp, historyXp)
    val level = totalXp / XP_PER_LEVEL + 1
    val streak = calculateStepGoalStreak(state.dailyStats, stepGoal)
    val bestSteps = max(todaySteps, state.dailyStats.maxOfOrNull { it.steps ?: 0 } ?: 0)

    return GamificationSummary(
        level = level,
        totalXp = totalXp,
        levelXp = totalXp % XP_PER_LEVEL,
        levelXpTarget = XP_PER_LEVEL,
        currentStreakDays = streak,
        goals = listOf(
            GoalProgress("Steps", todaySteps, stepGoal, ""),
            GoalProgress("Calories", todayCalories, DEFAULT_CALORIE_GOAL, "kcal"),
            GoalProgress("Sleep", todaySleep, DEFAULT_SLEEP_GOAL_MINUTES, "min"),
            GoalProgress("Activity", todayActivity, DEFAULT_ACTIVITY_GOAL, "")
        ),
        achievements = buildAchievements(
            state = state,
            todaySteps = todaySteps,
            todayCalories = todayCalories,
            todaySleep = todaySleep,
            hasHeartRate = hasHeartRate,
            streak = streak,
            bestSteps = bestSteps
        )
    )
}

fun calculateDailyXp(
    steps: Int,
    stepGoal: Int,
    calories: Int,
    sleepMinutes: Int,
    hasHeartRate: Boolean
): Int {
    val stepXp = min(max(steps, 0) / 100, 120)
    val goalXp = if (steps >= stepGoal && stepGoal > 0) 25 else 0
    val sleepXp = if (sleepMinutes >= DEFAULT_SLEEP_GOAL_MINUTES) 20 else 0
    val calorieXp = if (calories >= DEFAULT_CALORIE_GOAL) 15 else 0
    val heartXp = if (hasHeartRate) 10 else 0
    return stepXp + goalXp + sleepXp + calorieXp + heartXp
}

fun calculateStepGoalStreak(dailyStats: List<HealthEntry>, stepGoal: Int): Int {
    if (stepGoal <= 0 || dailyStats.isEmpty()) return 0

    val bestByDay = dailyStats
        .groupBy { dayKey(it.timestamp) }
        .mapValues { (_, entries) -> entries.maxOf { it.steps ?: 0 } }

    var streak = 0
    val cursor = Calendar.getInstance()
    while ((bestByDay[dayKey(cursor.timeInMillis)] ?: 0) >= stepGoal) {
        streak++
        cursor.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

private fun buildAchievements(
    state: WatchState,
    todaySteps: Int,
    todayCalories: Int,
    todaySleep: Int,
    hasHeartRate: Boolean,
    streak: Int,
    bestSteps: Int
): List<Achievement> {
    val hasAnySync = state.isConnected ||
        state.dailyStats.isNotEmpty() ||
        state.battery != null ||
        todaySteps > 0 ||
        todayCalories > 0

    return listOf(
        Achievement("First Sync", "Watch data received", hasAnySync),
        Achievement("5k Steps", "Reached 5,000 steps", todaySteps >= 5_000 || bestSteps >= 5_000),
        Achievement("10k Steps", "Reached 10,000 steps", todaySteps >= 10_000 || bestSteps >= 10_000),
        Achievement("Calorie Spark", "Burned 500 active kcal", todayCalories >= DEFAULT_CALORIE_GOAL),
        Achievement("Sleep Scout", "Logged at least 7h sleep", todaySleep >= DEFAULT_SLEEP_GOAL_MINUTES),
        Achievement("Heart Check", "Captured heart-rate data", hasHeartRate),
        Achievement("Connected Day", "Watch is connected", state.isConnected),
        Achievement("3-Day Streak", "Hit step goal 3 days in a row", streak >= 3),
        Achievement("7-Day Streak", "Hit step goal 7 days in a row", streak >= 7),
        Achievement("Personal Best Steps", "Logged a 15,000-step day", bestSteps >= 15_000)
    )
}

private fun dayKey(timestamp: Long): String = DAY_FORMAT.get()!!.format(Date(timestamp))

private val DAY_FORMAT = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
}
