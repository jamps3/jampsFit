package com.labbaslabs.jampsfit

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class TimedHeartRateSample(
    val bpm: Int,
    val timestamp: Long,
)

object DaFitHeartRateHistory {
    const val PAGE_COUNT = 8
    const val SAMPLES_PER_PAGE = 72

    private const val SAMPLE_INTERVAL_MINUTES = 5L

    fun decodePage(
        page: Int,
        values: List<Int>,
        nowMillis: Long,
        trackingStartedAtMillis: Long,
        measurementIntervalMinutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TimedHeartRateSample> {
        if (page !in 0 until PAGE_COUNT || values.size < SAMPLES_PER_PAGE || measurementIntervalMinutes <= 0) {
            return emptyList()
        }

        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val day = now.toLocalDate().minusDays(if (page >= 4) 1 else 0)
        val pageWithinDay = page % 4
        val firstSlot = pageWithinDay * SAMPLES_PER_PAGE
        val safeInterval = measurementIntervalMinutes.coerceAtLeast(SAMPLE_INTERVAL_MINUTES.toInt())
        val latestCompleteSample = nowMillis - (safeInterval + SAMPLE_INTERVAL_MINUTES) * 60_000L

        return values.take(SAMPLES_PER_PAGE).mapIndexedNotNull { index, bpm ->
            val slot = firstSlot + index
            val minuteOfDay = slot * SAMPLE_INTERVAL_MINUTES.toInt()
            if (minuteOfDay % safeInterval != 0 || bpm !in 30..220) return@mapIndexedNotNull null

            val localDateTime = LocalDateTime.of(day, LocalTime.MIDNIGHT).plusMinutes(minuteOfDay.toLong())
            val timestamp = localDateTime.atZone(zoneId).toInstant().toEpochMilli()
            if (timestamp < trackingStartedAtMillis || timestamp > latestCompleteSample) return@mapIndexedNotNull null

            TimedHeartRateSample(bpm = bpm, timestamp = timestamp)
        }
    }
}
