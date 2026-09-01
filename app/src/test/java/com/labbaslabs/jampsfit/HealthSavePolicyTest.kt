package com.labbaslabs.jampsfit

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthSavePolicyTest {
    @Test
    fun defaultIntervalIsOneHour() {
        assertEquals(60, HealthSavePolicy.DEFAULT_INTERVAL_MINUTES)
        assertEquals(60, HealthSavePolicy.normalizeIntervalMinutes(60))
    }

    @Test
    fun intervalIsClampedToSupportedRange() {
        assertEquals(1, HealthSavePolicy.normalizeIntervalMinutes(-10))
        assertEquals(1_440, HealthSavePolicy.normalizeIntervalMinutes(2_000))
    }

    @Test
    fun pendingWriteUsesConfiguredInterval() {
        assertEquals(30_000L, HealthSavePolicy.delayUntilNextWrite(100_000L, 130_000L, 1))
        assertEquals(0L, HealthSavePolicy.delayUntilNextWrite(100_000L, 160_000L, 1))
    }
}
