package com.labbaslabs.jampsfit

internal object DaFitBloodPressureMeasurement {
    const val NAME = "Blood Pressure"
    const val TIMEOUT_MS = 90_000L

    fun startPacket(): ByteArray = nativePacket(COMMAND, 0x00, 0x00, 0x00)

    fun stopPacket(): ByteArray = nativePacket(COMMAND, 0xFF, 0xFF, 0xFF)

    private const val COMMAND = 0x69
}

internal object DaFitBloodPressureSchedule {
    val supportedIntervalsMinutes = listOf(0, 5, 15, 30, 60, 180, 360, 540, 720, 1_440)

    fun isSupported(intervalMinutes: Int): Boolean = intervalMinutes in supportedIntervalsMinutes

    fun nextDelayMillis(intervalMinutes: Int, lastStartedAt: Long, now: Long): Long {
        require(intervalMinutes > 0 && isSupported(intervalMinutes))
        val anchor = lastStartedAt.takeIf { it > 0L } ?: now
        return (anchor + intervalMinutes * 60_000L - now).coerceAtLeast(0L)
    }
}

internal fun nativePacket(command: Int, vararg payload: Int): ByteArray =
    ByteArray(5 + payload.size).apply {
        this[0] = 0xFE.toByte()
        this[1] = 0xEA.toByte()
        this[2] = 0x20.toByte()
        this[3] = size.toByte()
        this[4] = command.toByte()
        payload.forEachIndexed { index, value ->
            this[5 + index] = (value and 0xFF).toByte()
        }
    }
