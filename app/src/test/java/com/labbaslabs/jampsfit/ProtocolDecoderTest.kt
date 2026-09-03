package com.labbaslabs.jampsfit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolDecoderTest {
    @Test
    fun decodesBatteryLevel() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_BATTERY, byteArrayOf(87.toByte()))

        assertEquals(ProtocolDecoder.DecodedResult.Battery(87), results.single())
    }

    @Test
    fun decodesCurrentDailyTotals() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(
            ProtocolDecoder.UUID_FEE3,
            byteArrayOf(
                0xFE.toByte(),
                0xEA.toByte(),
                0x20,
                0x0F,
                0x33,
                0x00,
                0x39,
                0x1C,
                0x00,
                0x2C,
                0x01,
                0x00,
                0x58,
                0x02,
                0x00,
            ),
        )

        assertEquals(ProtocolDecoder.DecodedResult.DailyTotals(7225, 300, 600), results.single())
    }

    @Test
    fun decodesMusicRemoteEvent() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_FEE3, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x06, 0x67, 0x02))

        assertEquals(ProtocolDecoder.DecodedResult.RemoteEvent("Next Track"), results.single())
    }

    @Test
    fun ignoresPreviousDayDailyTotals() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_FEE3, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x0F, 0x33, 0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        assertTrue(results.isEmpty())
    }

    @Test
    fun ignoresIdleHeartRateZeros() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_HEART_RATE, byteArrayOf(0x00, 0x00, 0x00))

        assertTrue(results.isEmpty())
    }

    @Test
    fun decodesExerciseHeartRateStream() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_HEART_RATE, byteArrayOf(0x00, 0x5E, 0x00))

        assertEquals(ProtocolDecoder.DecodedResult.HeartRate(94), results.single())
    }

    @Test
    fun ignoresFee3CommandAckWithoutPayload() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_FEE3, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x06, 0x29, 0x03))

        assertTrue(results.isEmpty())
    }

    @Test
    fun decodesCurrentAutoHeartRateIntervalQuery() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(
            ProtocolDecoder.UUID_FEE3,
            byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x06, 0x2F, 0x01),
        )

        assertEquals(ProtocolDecoder.DecodedResult.AutoHeartRate(5), results.single())
    }

    @Test
    fun decodesBloodPressureMeasurementResult() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(
            ProtocolDecoder.UUID_FEE3,
            byteArrayOf(
                0xFE.toByte(), 0xEA.toByte(), 0x20, 0x08, 0x69, 0x00, 0x77, 0x4F
            ),
        )

        assertEquals(ProtocolDecoder.DecodedResult.BloodPressure(119, 79), results.single())
    }

    @Test
    fun ignoresBloodPressureCommandAcknowledgement() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(
            ProtocolDecoder.UUID_FEE3,
            byteArrayOf(
                0xFE.toByte(), 0xEA.toByte(), 0x20, 0x08, 0x69, 0x00, 0x00, 0x00
            ),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun ignoresBloodPressureStopAcknowledgement() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(
            ProtocolDecoder.UUID_FEE3,
            byteArrayOf(
                0xFE.toByte(), 0xEA.toByte(), 0x20, 0x08, 0x69, 0x00, 0xFF.toByte(), 0xFF.toByte()
            ),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun reassemblesAndDecodesHeartRateHistoryPage() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)
        val values = List(DaFitHeartRateHistory.SAMPLES_PER_PAGE) { 60 + it % 40 }
        val packet = ByteArray(6 + values.size).apply {
            this[0] = 0xFE.toByte()
            this[1] = 0xEA.toByte()
            this[2] = 0x20
            this[3] = size.toByte()
            this[4] = 0x35
            this[5] = 0x03
            values.forEachIndexed { index, value -> this[index + 6] = value.toByte() }
        }

        packet.asList().chunked(20).forEach { fragment ->
            decoder.decode(ProtocolDecoder.UUID_FEE3, fragment.toByteArray())
        }

        assertEquals(
            ProtocolDecoder.DecodedResult.HeartRateHistoryPage(page = 3, values = values),
            results.single(),
        )
    }

    @Test
    fun decodesWatchExerciseSummaryPacket() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(
            ProtocolDecoder.UUID_FEE3,
            byteArrayOf(
                0xFE.toByte(), 0xEA.toByte(), 0x20, 0x13, 0x34,
                0x02,
                0xE4.toByte(), 0x00,
                0x0F, 0x00,
                0x5E, 0x50, 0x70,
                0xD2.toByte(), 0x04, 0x00,
                0xF4.toByte(), 0x01, 0x00
            )
        )

        assertEquals(
            ProtocolDecoder.DecodedResult.WatchExerciseSummary(
                sportType = 2,
                durationSeconds = 228,
                calories = 15,
                averageBpm = 94,
                minBpm = 80,
                maxBpm = 112,
                steps = 1234,
                distance = 500
            ),
            results.single()
        )
    }
}
