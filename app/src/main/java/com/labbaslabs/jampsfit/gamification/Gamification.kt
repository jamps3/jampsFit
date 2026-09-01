package com.labbaslabs.jampsfit.gamification

import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EventEntity
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
const val ACHIEVEMENT_SCOPE_GENERAL = "General"
const val ACHIEVEMENT_SCOPE_FESTIVAL = "Festival"

data class GamificationSummary(
    val level: Int,
    val totalXp: Int,
    val levelXp: Int,
    val levelXpTarget: Int,
    val currentStreakDays: Int,
    val goals: List<GoalProgress>,
    val achievements: List<Achievement>,
    val weeklySteps: Int,
    val weeklySleepNights: Int,
    val bestSteps: Int,
    val bestSleepMinutes: Int
    ,val questTitle: String,
    val questValue: Int,
    val questTarget: Int,
    val milestones: List<String>
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
    val unlocked: Boolean,
    val id: String = title,
    val scope: String = ACHIEVEMENT_SCOPE_GENERAL,
    val group: String = ACHIEVEMENT_SCOPE_GENERAL,
    val progressValue: Int? = null,
    val progressTarget: Int? = null,
    val progressUnit: String = ""
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
    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val weekEntries = state.dailyStats.filter { it.timestamp >= weekStart }

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
        ),
        weeklySteps = weekEntries.sumOf { it.steps ?: 0 },
        weeklySleepNights = weekEntries.count { (it.sleepMinutes ?: 0) >= DEFAULT_SLEEP_GOAL_MINUTES },
        bestSteps = bestSteps,
        bestSleepMinutes = state.dailyStats.maxOfOrNull { it.sleepMinutes ?: 0 } ?: todaySleep,
        questTitle = if (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % 2 == 0) "Reach 7,500 steps today" else "Capture a heart-rate reading",
        questValue = if (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % 2 == 0) todaySteps else if (hasHeartRate) 1 else 0,
        questTarget = if (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % 2 == 0) 7_500 else 1,
        milestones = buildList {
            if (bestSteps >= 5_000) add("5k steps reached")
            if (streak >= 3) add("3-day step streak")
            state.recentEvents.filter { it.endTime != null }.take(3).forEach { add("${it.name} completed") }
        }.take(5)
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
    ) + buildFestivalAchievements(state.recentEvents, state.dailyStats)
}

private fun dayKey(timestamp: Long): String = DAY_FORMAT.get()!!.format(Date(timestamp))

private fun buildFestivalAchievements(events: List<EventEntity>, dailyStats: List<HealthEntry>): List<Achievement> {
    val dancingEvents = events.filter { it.type == EVENT_TYPE_DANCING }
    val completed = dancingEvents.filter { it.endTime != null }
    val completedByDay = completed.groupBy { dayKey(it.startTime) }
    val festivalDays = completedByDay.keys.size
    val totalSteps = completed.sumOf { it.stepDelta }
    val totalDurationMinutes = completed.sumOf { it.durationSeconds } / 60
    val totalActiveCalories = completed.sumOf { it.activeCalories }
    val totalHeartRateSamples = completed.sumOf { it.heartRateSamples }
    val heartRateEventCount = completed.count { it.heartRateSamples > 0 }
    val maxTempoRange = completed.maxOfOrNull { event ->
        val minBpm = event.minBpm
        val maxBpm = event.maxBpm
        if (minBpm != null && maxBpm != null) maxBpm - minBpm else 0
    } ?: 0
    val maxSingleDurationMinutes = completed.maxOfOrNull { it.durationSeconds / 60 } ?: 0
    val maxSetsInDay = completedByDay.values.maxOfOrNull { it.size } ?: 0
    val firstFestivalDays = completedByDay.keys.sorted().take(4)
    val completedDayRanks = firstFestivalDays.mapIndexed { index, day -> day to index + 1 }.toMap()
    val recoveryDays = completed
        .flatMap { listOf(dayKey(it.startTime), nextDayKey(it.startTime)) }
        .toSet()
    val sixHourRecoveryNights = dailyStats
        .filter { (it.sleepMinutes ?: 0) >= 6 * 60 && dayKey(it.timestamp) in recoveryDays }
        .map { dayKey(it.timestamp) }
        .toSet()
        .size
    val hasSevenHourRecovery = dailyStats.any { (it.sleepMinutes ?: 0) >= 7 * 60 && dayKey(it.timestamp) in recoveryDays }

    val coreAchievements = listOf(
        festivalAchievement("core.wristband-on", "Wristband On", "Started a dancing event", dancingEvents.isNotEmpty(), "Core"),
        festivalAchievement("core.first-set", "First Set", "Completed a 10-minute dancing event", maxSingleDurationMinutes >= 10, "Core", maxSingleDurationMinutes, 10, "min"),
        festivalAchievement("core.main-stage", "Main Stage", "Completed a 60-minute dancing event", maxSingleDurationMinutes >= 60, "Core", maxSingleDurationMinutes, 60, "min"),
        festivalAchievement("core.back-to-back", "Back-to-Back Sets", "Completed two dancing events in one day", maxSetsInDay >= 2, "Core", maxSetsInDay, 2, "sets"),
        festivalAchievement("core.triple-day", "Triple Set Day", "Completed three dancing events in one day", maxSetsInDay >= 3, "Core", maxSetsInDay, 3, "sets"),
        festivalAchievement("core.two-day", "Two-Day Groove", "Completed dancing events on 2 days", festivalDays >= 2, "Core", festivalDays, 2, "days"),
        festivalAchievement("core.three-day", "Three-Day Groove", "Completed dancing events on 3 days", festivalDays >= 3, "Core", festivalDays, 3, "days"),
        festivalAchievement("core.four-day", "Four-Day Pass", "Completed dancing events on 4 days", festivalDays >= 4, "Core", festivalDays, 4, "days"),
        festivalAchievement("core.opening-day", "Opening Day", "Completed an event on festival day 1", completedDayRanks.containsValue(1), "Core"),
        festivalAchievement("core.day-two", "Day Two Stamp", "Completed an event on festival day 2", completedDayRanks.containsValue(2), "Core", festivalDays, 2, "days"),
        festivalAchievement("core.day-three", "Day Three Stamp", "Completed an event on festival day 3", completedDayRanks.containsValue(3), "Core", festivalDays, 3, "days"),
        festivalAchievement("core.closing-day", "Closing Day Stamp", "Completed an event on festival day 4", completedDayRanks.containsValue(4), "Core", festivalDays, 4, "days"),
        festivalAchievement("core.morning", "Morning Warmup", "Started a dancing event before noon", completed.any { localHour(it.startTime) in 5..11 }, "Core"),
        festivalAchievement("core.afternoon", "Afternoon Set", "Started a dancing event after noon", completed.any { localHour(it.startTime) in 12..17 }, "Core"),
        festivalAchievement("core.night", "Night Set", "Started a dancing event after 9 PM", completed.any { localHour(it.startTime) >= 21 }, "Core"),
        festivalAchievement("core.midnight", "After Midnight", "Danced across midnight or started before 2 AM", completed.any { crossesMidnight(it) || localHour(it.startTime) in 0..1 }, "Core")
    )

    val dancefloorAchievements = DANCEFLOOR_THRESHOLDS.map { threshold ->
        val label = "${threshold / 1_000}k Dancefloor"
        festivalAchievement(
            id = "dancefloor.$threshold",
            title = label,
            detail = "Recorded ${formatNumber(threshold)} event steps",
            unlocked = totalSteps >= threshold,
            group = "Dancefloor",
            progressValue = totalSteps,
            progressTarget = threshold,
            progressUnit = "steps"
        )
    }

    val totalDurationAchievements = TOTAL_DURATION_MINUTE_THRESHOLDS.map { threshold ->
        festivalAchievement(
            id = "duration.total.$threshold",
            title = "${durationTitle(threshold)} Groove",
            detail = "Recorded ${durationDetail(threshold)} total dancing time",
            unlocked = totalDurationMinutes >= threshold,
            group = "Duration",
            progressValue = totalDurationMinutes,
            progressTarget = threshold,
            progressUnit = "min"
        )
    }

    val setCountAchievements = SET_COUNT_THRESHOLDS.map { threshold ->
        festivalAchievement(
            id = "sets.$threshold",
            title = "$threshold Set Festival",
            detail = "Completed $threshold dancing events",
            unlocked = completed.size >= threshold,
            group = "Sets",
            progressValue = completed.size,
            progressTarget = threshold,
            progressUnit = "sets"
        )
    }

    val singleDurationAchievements = SINGLE_SET_MINUTE_THRESHOLDS.map { threshold ->
        festivalAchievement(
            id = "duration.single.$threshold",
            title = "${durationTitle(threshold)} Set",
            detail = "Completed one ${durationDetail(threshold)} dancing event",
            unlocked = maxSingleDurationMinutes >= threshold,
            group = "Long Sets",
            progressValue = maxSingleDurationMinutes,
            progressTarget = threshold,
            progressUnit = "min"
        )
    }

    val energyAchievements = ENERGY_THRESHOLDS.map { threshold ->
        festivalAchievement(
            id = "energy.$threshold",
            title = if (threshold == 250) "Heat Wave" else "$threshold kcal Heat",
            detail = "Recorded $threshold active kcal across events",
            unlocked = totalActiveCalories >= threshold,
            group = "Energy",
            progressValue = totalActiveCalories,
            progressTarget = threshold,
            progressUnit = "kcal"
        )
    }

    val heartAchievements = listOf(
        festivalAchievement("heart.beat-keeper", "Beat Keeper", "Captured heart-rate data during an event", heartRateEventCount >= 1, "Heart", heartRateEventCount, 1, "events"),
        festivalAchievement("heart.two-events", "Two Heart Sets", "Captured heart-rate data in 2 events", heartRateEventCount >= 2, "Heart", heartRateEventCount, 2, "events"),
        festivalAchievement("heart.four-events", "Four Heart Sets", "Captured heart-rate data in 4 events", heartRateEventCount >= 4, "Heart", heartRateEventCount, 4, "events"),
        festivalAchievement("heart.eight-events", "Eight Heart Sets", "Captured heart-rate data in 8 events", heartRateEventCount >= 8, "Heart", heartRateEventCount, 8, "events"),
        festivalAchievement("heart.samples.25", "25 Beat Samples", "Captured 25 heart-rate samples", totalHeartRateSamples >= 25, "Heart", totalHeartRateSamples, 25, "samples"),
        festivalAchievement("heart.samples.50", "50 Beat Samples", "Captured 50 heart-rate samples", totalHeartRateSamples >= 50, "Heart", totalHeartRateSamples, 50, "samples"),
        festivalAchievement("heart.samples.100", "100 Beat Samples", "Captured 100 heart-rate samples", totalHeartRateSamples >= 100, "Heart", totalHeartRateSamples, 100, "samples"),
        festivalAchievement("heart.samples.250", "250 Beat Samples", "Captured 250 heart-rate samples", totalHeartRateSamples >= 250, "Heart", totalHeartRateSamples, 250, "samples"),
        festivalAchievement("heart.samples.500", "500 Beat Samples", "Captured 500 heart-rate samples", totalHeartRateSamples >= 500, "Heart", totalHeartRateSamples, 500, "samples"),
        festivalAchievement("heart.range.20", "Tempo Story", "Captured a 20 BPM range in one event", maxTempoRange >= 20, "Heart", maxTempoRange, 20, "BPM"),
        festivalAchievement("heart.range.30", "Big Tempo Story", "Captured a 30 BPM range in one event", maxTempoRange >= 30, "Heart", maxTempoRange, 30, "BPM"),
        festivalAchievement("heart.range.40", "Wild Tempo Story", "Captured a 40 BPM range in one event", maxTempoRange >= 40, "Heart", maxTempoRange, 40, "BPM")
    )

    val dataAchievements = listOf(
        festivalAchievement("data.steps", "Step Trace", "Captured event steps", completed.any { it.stepDelta > 0 }, "Data"),
        festivalAchievement("data.activity", "Activity Trace", "Captured event activity", completed.any { it.activityDelta > 0 }, "Data"),
        festivalAchievement("data.distance", "Distance Trace", "Captured event distance", completed.any { it.distanceDelta > 0 }, "Data"),
        festivalAchievement("data.calories", "Calorie Trace", "Captured event calories", completed.any { it.activeCalories > 0 }, "Data"),
        festivalAchievement("data.duo", "Sensor Duo", "Captured two metric types in one event", completed.any { metricCount(it) >= 2 }, "Data"),
        festivalAchievement("data.trio", "Sensor Trio", "Captured three metric types in one event", completed.any { metricCount(it) >= 3 }, "Data"),
        festivalAchievement("data.full", "Full Sensor Set", "Captured steps, distance, calories, and HR in one event", completed.any { metricCount(it) >= 4 }, "Data"),
        festivalAchievement("data.collector", "Data Collector", "Captured steps, calories, and heart-rate in one event", completed.any {
            it.stepDelta > 0 && it.activeCalories > 0 && it.heartRateSamples > 0
        }, "Data")
    )

    val recoveryAchievements = listOf(
        festivalAchievement("recovery.win", "Recovery Win", "Logged 6h sleep after a festival day", sixHourRecoveryNights >= 1, "Recovery", sixHourRecoveryNights, 1, "nights"),
        festivalAchievement("recovery.seven-hour", "Seven-Hour Reset", "Logged 7h sleep after a festival day", hasSevenHourRecovery, "Recovery"),
        festivalAchievement("recovery.two-nights", "Two Recovery Nights", "Logged 6h sleep after 2 festival days", sixHourRecoveryNights >= 2, "Recovery", sixHourRecoveryNights, 2, "nights"),
        festivalAchievement("recovery.three-nights", "Three Recovery Nights", "Logged 6h sleep after 3 festival days", sixHourRecoveryNights >= 3, "Recovery", sixHourRecoveryNights, 3, "nights"),
        festivalAchievement("recovery.four-nights", "Four Recovery Nights", "Logged 6h sleep after 4 festival days", sixHourRecoveryNights >= 4, "Recovery", sixHourRecoveryNights, 4, "nights")
    )

    return coreAchievements +
        dancefloorAchievements +
        totalDurationAchievements +
        setCountAchievements +
        singleDurationAchievements +
        energyAchievements +
        heartAchievements +
        dataAchievements +
        recoveryAchievements
}

private fun nextDayKey(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
        add(Calendar.DAY_OF_YEAR, 1)
    }
    return dayKey(calendar.timeInMillis)
}

private fun festivalAchievement(
    id: String,
    title: String,
    detail: String,
    unlocked: Boolean,
    group: String,
    progressValue: Int? = null,
    progressTarget: Int? = null,
    progressUnit: String = ""
): Achievement = Achievement(
    title = title,
    detail = detail,
    unlocked = unlocked,
    id = "festival.$id",
    scope = ACHIEVEMENT_SCOPE_FESTIVAL,
    group = group,
    progressValue = progressValue,
    progressTarget = progressTarget,
    progressUnit = progressUnit
)

private fun localHour(timestamp: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return calendar.get(Calendar.HOUR_OF_DAY)
}

private fun crossesMidnight(event: EventEntity): Boolean {
    val endTime = event.endTime ?: return false
    return dayKey(event.startTime) != dayKey(endTime)
}

private fun metricCount(event: EventEntity): Int = listOf(
    event.stepDelta > 0,
    event.activityDelta > 0,
    event.distanceDelta > 0,
    event.activeCalories > 0,
    event.heartRateSamples > 0
).count { it }

private fun formatNumber(value: Int): String = "%,d".format(Locale.US, value)

private fun durationTitle(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h${remainder}m"
}

private fun durationDetail(minutes: Int): String {
    if (minutes < 60) return "$minutes-minute"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}-hour" else "${hours}-hour ${remainder}-minute"
}

private val DANCEFLOOR_THRESHOLDS = (5_000..50_000 step 5_000).toList() + listOf(60_000, 70_000, 80_000, 90_000, 100_000)
private val TOTAL_DURATION_MINUTE_THRESHOLDS = listOf(10, 30, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720, 960, 1200, 1440)
private val SET_COUNT_THRESHOLDS = listOf(2, 3, 4, 5, 7, 10, 15, 20, 30, 50)
private val SINGLE_SET_MINUTE_THRESHOLDS = listOf(15, 30, 45, 90, 120, 180, 240, 360)
private val ENERGY_THRESHOLDS = listOf(50, 100, 150, 250, 500, 750, 1000, 1500, 2000, 3000, 4000, 5000)

private val DAY_FORMAT = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
}
