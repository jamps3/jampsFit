package com.labbaslabs.jampsfit

import org.junit.Assert.*
import org.junit.Test

class HeartRateAutomationTest {
    @Test fun everyOfferedIntervalHasAWatchCommand() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), HeartRateAutomation.supportedIntervals.map { HeartRateAutomation.intervalCode(it) })
        assertNull(HeartRateAutomation.intervalCode(180))
    }

    @Test fun automaticHistoryDelayDoesNotTriggerFalseReminder() {
        assertFalse(HeartRateAutomation.reminderDue(65 * 60_000L, 0, 60, 5))
        assertTrue(HeartRateAutomation.reminderDue(70 * 60_000L, 0, 60, 5))
        assertTrue(HeartRateAutomation.reminderDue(60 * 60_000L, 0, 60, 0))
        assertFalse(HeartRateAutomation.reminderDue(70 * 60_000L, 65 * 60_000L, 60, 5))
    }
}
