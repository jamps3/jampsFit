package com.labbaslabs.jampsfit

object HealthSavePolicy {
    const val DEFAULT_INTERVAL_MINUTES = 60
    const val MIN_INTERVAL_MINUTES = 1
    const val MAX_INTERVAL_MINUTES = 1_440

    fun normalizeIntervalMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    fun delayUntilNextWrite(lastWriteTime: Long, now: Long, intervalMinutes: Int): Long {
        val intervalMs = normalizeIntervalMinutes(intervalMinutes) * 60_000L
        return (lastWriteTime + intervalMs - now).coerceAtLeast(0L)
    }
}
