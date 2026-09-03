package com.labbaslabs.jampsfit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DaFitMeasurementProtocolTest {
    @Test
    fun buildsBloodPressureStartPacket() {
        assertArrayEquals(
            byteArrayOf(
                0xFE.toByte(), 0xEA.toByte(), 0x20, 0x08, 0x69, 0x00, 0x00, 0x00
            ),
            DaFitBloodPressureMeasurement.startPacket(),
        )
    }

    @Test
    fun buildsBloodPressureStopPacket() {
        assertArrayEquals(
            byteArrayOf(
                0xFE.toByte(), 0xEA.toByte(), 0x20, 0x08, 0x69, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
            ),
            DaFitBloodPressureMeasurement.stopPacket(),
        )
    }

    @Test
    fun supportsRequestedAutomaticBloodPressureIntervals() {
        assertArrayEquals(
            intArrayOf(0, 5, 15, 30, 60, 180, 360, 540, 720, 1_440),
            DaFitBloodPressureSchedule.supportedIntervalsMinutes.toIntArray(),
        )
    }

    @Test
    fun calculatesDelayFromPreviousMeasurementStart() {
        val now = 1_000_000L

        val delay = DaFitBloodPressureSchedule.nextDelayMillis(
            intervalMinutes = 15,
            lastStartedAt = now - 5 * 60_000L,
            now = now,
        )

        assertEquals(10 * 60_000L, delay)
    }

    @Test
    fun overdueMeasurementIsDueImmediately() {
        val now = 1_000_000L

        val delay = DaFitBloodPressureSchedule.nextDelayMillis(
            intervalMinutes = 5,
            lastStartedAt = now - 10 * 60_000L,
            now = now,
        )

        assertEquals(0L, delay)
    }

    @Test
    fun rejectsUnsupportedAutomaticBloodPressureInterval() {
        assertFalse(DaFitBloodPressureSchedule.isSupported(10))
    }
}
