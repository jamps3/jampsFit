package com.labbaslabs.jampsfit

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DaFitHeartRateHistoryTest {
    private val zone = ZoneId.of("Europe/Helsinki")

    @Test
    fun mapsTodayPageSlotsAndIgnoresIncompleteValues() {
        val now = millis("2026-07-08T07:12:00")
        val values = MutableList(DaFitHeartRateHistory.SAMPLES_PER_PAGE) { 0 }.apply {
            this[12] = 81 // 07:00
            this[13] = 82 // 07:05, still inside the safety lag
        }

        val samples = DaFitHeartRateHistory.decodePage(
            page = 1,
            values = values,
            nowMillis = now,
            trackingStartedAtMillis = millis("2026-07-08T00:00:00"),
            measurementIntervalMinutes = 5,
            zoneId = zone,
        )

        assertEquals(listOf(TimedHeartRateSample(81, millis("2026-07-08T07:00:00"))), samples)
    }

    @Test
    fun mapsPagesFourToSevenToYesterday() {
        val values = MutableList(DaFitHeartRateHistory.SAMPLES_PER_PAGE) { 0 }.apply { this[0] = 74 }

        val samples = DaFitHeartRateHistory.decodePage(
            page = 4,
            values = values,
            nowMillis = millis("2026-07-08T12:00:00"),
            trackingStartedAtMillis = millis("2026-07-06T00:00:00"),
            measurementIntervalMinutes = 5,
            zoneId = zone,
        )

        assertEquals(listOf(TimedHeartRateSample(74, millis("2026-07-07T00:00:00"))), samples)
    }

    @Test
    fun honorsMeasurementIntervalAndTrackingStart() {
        val values = MutableList(DaFitHeartRateHistory.SAMPLES_PER_PAGE) { 0 }.apply {
            this[1] = 70 // 00:05, not a 10-minute slot
            this[2] = 71 // 00:10, before tracking started
            this[4] = 72 // 00:20
        }

        val samples = DaFitHeartRateHistory.decodePage(
            page = 0,
            values = values,
            nowMillis = millis("2026-07-08T01:00:00"),
            trackingStartedAtMillis = millis("2026-07-08T00:15:00"),
            measurementIntervalMinutes = 10,
            zoneId = zone,
        )

        assertEquals(listOf(TimedHeartRateSample(72, millis("2026-07-08T00:20:00"))), samples)
    }

    private fun millis(localDateTime: String): Long =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()
}
