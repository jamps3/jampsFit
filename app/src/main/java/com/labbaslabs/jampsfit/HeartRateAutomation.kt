package com.labbaslabs.jampsfit

object HeartRateAutomation {
    // Same codes decoded from the watch's 0x2F settings response.
    val supportedIntervals = listOf(0, 5, 10, 15, 30, 60)

    fun intervalCode(minutes: Int): Int? = supportedIntervals.indexOf(minutes).takeIf { it >= 0 }

    fun reminderDue(now: Long, measuredAt: Long, reminderMinutes: Int, automaticMinutes: Int): Boolean {
        val historyDelayMinutes = if (automaticMinutes > 0) automaticMinutes + 5L else 0L
        return now - measuredAt >= (reminderMinutes + historyDelayMinutes) * 60_000L
    }
}
