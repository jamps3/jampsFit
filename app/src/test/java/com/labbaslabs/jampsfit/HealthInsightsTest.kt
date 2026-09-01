package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.health.calculateHealthInsights
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthInsightsTest {
    @Test
    fun lowSleepProducesRecoveryWarning() {
        val state = WatchState(sleepMinutes = 300, dailyStats = listOf(HealthEntry(sleepMinutes = 300)))
        val insights = calculateHealthInsights(state)
        assertTrue(insights.recoveryScore < 70)
        assertTrue(insights.headline.contains("sleep"))
    }
}
