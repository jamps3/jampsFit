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
    fun ignoresFee3CommandAckWithoutPayload() {
        val results = mutableListOf<ProtocolDecoder.DecodedResult>()
        val decoder = ProtocolDecoder(results::add)

        decoder.decode(ProtocolDecoder.UUID_FEE3, byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x06, 0x29, 0x03))

        assertTrue(results.isEmpty())
    }
}
