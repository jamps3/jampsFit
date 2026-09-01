package com.labbaslabs.jampsfit.health

import com.labbaslabs.jampsfit.WatchState

data class HealthInsights(
    val recoveryScore: Int,
    val averageHeartRate: Int?,
    val latestSleepMinutes: Int,
    val activeDaysThisWeek: Int,
    val headline: String
)

fun calculateHealthInsights(state: WatchState): HealthInsights {
    val recent = state.dailyStats.take(7)
    val sleep = state.sleepMinutes ?: recent.firstOrNull()?.sleepMinutes ?: 0
    val averageHr = state.heartRateHistory.takeLast(20).map { it.value }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt()
    val activeDays = recent.count { (it.steps ?: 0) >= 5_000 || (it.activityCount ?: 0) >= 50 }
    val sleepScore = (sleep / 420f * 45f).toInt().coerceIn(0, 45)
    val activityScore = (activeDays / 5f * 35f).toInt().coerceIn(0, 35)
    val hrScore = if (averageHr == null) 0 else if (averageHr in 50..100) 20 else 10
    val score = (sleepScore + activityScore + hrScore).coerceIn(0, 100)
    val headline = when {
        sleep < 360 -> "Prioritize recovery sleep"
        activeDays < 3 -> "A little more movement would help"
        averageHr != null && averageHr > 100 -> "Heart rate trend is elevated"
        else -> "Your recovery trend looks steady"
    }
    return HealthInsights(score, averageHr, sleep, activeDays, headline)
}
