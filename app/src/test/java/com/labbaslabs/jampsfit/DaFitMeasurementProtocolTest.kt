package com.labbaslabs.jampsfit

import org.junit.Assert.assertArrayEquals
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
}
