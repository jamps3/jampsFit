package com.labbaslabs.jampsfit

import android.util.Log
import com.labbaslabs.jampsfit.database.HealthEntry
import java.util.*

private val SLEEP_STAGES = setOf(0x01, 0x02, 0x03)

class ProtocolDecoder(private val onResult: (DecodedResult) -> Unit) {

    private val fee3PacketReassembler = Fee3PacketReassembler()

    sealed class DecodedResult {
        data class Battery(val level: Int) : DecodedResult()
        data class HeartRate(val bpm: Int) : DecodedResult()
        data class SpO2(val percent: Int) : DecodedResult()
        data class BloodPressure(val systolic: Int, val diastolic: Int) : DecodedResult()
        data class Activity(val seq: Int, val activityCount: Int, val distance: Int, val calories: Int) : DecodedResult()
        data class DailyTotals(val steps: Int, val distance: Int, val calories: Int) : DecodedResult()
        data class HourlyActivity(val bucket: Int, val stepsDown: Int, val stepsUp: Int, val stepsOther: Int) : DecodedResult()
        data class SleepSummary(val total: Int, val deep: Int, val light: Int) : DecodedResult()
        data class SleepBoundaries(val segments: List<SleepSegment>, val total: Int, val deep: Int, val light: Int) : DecodedResult()
        data class DeviceInfo(val name: String?, val firmware: String?) : DecodedResult()
        data class AlarmSettings(val alarms: List<WatchAlarm>) : DecodedResult()
        data class StepGoal(val goal: Int) : DecodedResult()
        data class AutoLock(val seconds: Int) : DecodedResult()
        data class RemoteEvent(val event: String) : DecodedResult()
        data class AutoHeartRate(val minutes: Int) : DecodedResult()
        data class HeartRateHistoryPage(val page: Int, val values: List<Int>) : DecodedResult()
        data class PowerSave(val enabled: Boolean) : DecodedResult()
        data class WatchExerciseSummary(
            val sportType: Int,
            val durationSeconds: Int,
            val calories: Int,
            val averageBpm: Int?,
            val minBpm: Int?,
            val maxBpm: Int?,
            val steps: Int,
            val distance: Int
        ) : DecodedResult()
        object ShutterEvent : DecodedResult()
    }

    fun decode(uuid: UUID, data: ByteArray) {
        when (uuid) {
            UUID_BATTERY -> {
                if (data.isNotEmpty()) onResult(DecodedResult.Battery(data[0].toInt() and 0xFF))
            }
            UUID_HEART_RATE -> {
                parseStandardHeartRate(data)?.let { if (it > 0) onResult(DecodedResult.HeartRate(it)) }
            }
            UUID_FEE1, UUID_FEA1 -> {
                parseActivityPacket(data)?.let { onResult(it) }
            }
            UUID_FEE3 -> {
                fee3PacketReassembler.accept(data)?.let(::parseFee3Packet)
            }
        }
    }

    private fun parseFee3Packet(data: ByteArray) {
        if (data.size < 5 || data[0] != 0xFE.toByte() || data[1] != 0xEA.toByte() || data[2] != 0x20.toByte()) return
        
        when (data[4].toInt() and 0xFF) {
            0x1F, 0x2F -> parseAutoHeartRate(data)
            0x21 -> parseAlarmQueryResponse(data)
            0x26 -> parseStepGoalResponse(data)
            0x32 -> if (data.size >= 8) parseSleepBoundaryPacket(data)
            0x33 -> if (data.size >= 15) parseDailyTotalsPacket(data)
            0x34 -> if (data.size >= 19) parseWatchExerciseSummaryPacket(data)
            0x35 -> parseHeartRateHistoryPage(data)
            0x59 -> if (data.size >= 54) parseHourlyActivityPacket(data)
            0x5A -> parseDeviceInfoPacket(data)
            0x8D -> data.getOrNull(5)?.toInt()?.and(0xFF)?.let { onResult(DecodedResult.AutoLock(it)) }
            0x6D -> extractHeartRateCandidate(data)?.let { onResult(DecodedResult.HeartRate(it)) }
            0x6B -> if (data.size > 5) {
                val spo2 = data[5].toInt() and 0xFF
                if (spo2 > 0) onResult(DecodedResult.SpO2(spo2))
            }
            0x69 -> if (data.size > 7) {
                val systolic = data[6].toInt() and 0xFF
                val diastolic = data[7].toInt() and 0xFF
                onResult(DecodedResult.BloodPressure(systolic, diastolic))
            }
            0x66 -> onResult(DecodedResult.ShutterEvent)
            0x67 -> {
                val event = when (data.getOrNull(5)?.toInt()?.and(0xFF)) {
                    0x01 -> "Previous Track"; 0x02 -> "Next Track"; 0x06 -> "Play/Pause"; else -> null
                }
                if (event != null) onResult(DecodedResult.RemoteEvent(event))
            }
            0xA4 -> if (data.size > 6) onResult(DecodedResult.PowerSave(data[5].toInt() == 0x01))
        }
    }

    private fun parseAutoHeartRate(data: ByteArray) {
        val interval = when (data.getOrNull(5)?.toInt()?.and(0xFF)) {
            0x00 -> 0
            0x01 -> 5
            0x02 -> 10
            0x03 -> 15
            0x04 -> 30
            0x05 -> 60
            else -> null
        }
        interval?.let { onResult(DecodedResult.AutoHeartRate(it)) }
    }

    private fun parseHeartRateHistoryPage(data: ByteArray) {
        val page = data.getOrNull(5)?.toInt()?.and(0xFF) ?: return
        if (page !in 0 until DaFitHeartRateHistory.PAGE_COUNT || data.size < 6 + DaFitHeartRateHistory.SAMPLES_PER_PAGE) return
        val values = data.copyOfRange(6, 6 + DaFitHeartRateHistory.SAMPLES_PER_PAGE).map { it.toInt() and 0xFF }
        onResult(DecodedResult.HeartRateHistoryPage(page, values))
    }

    private fun parseActivityPacket(data: ByteArray): DecodedResult.Activity? {
        val b = when {
            data.size == 10 && data[0] == 0x07.toByte() -> data.copyOfRange(1, 10)
            data.size == 9 -> data
            else -> return null
        }
        val seq = b[0].toInt() and 0xFF
        val activityCount = (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
        val distance = (b[3].toInt() and 0xFF) or ((b[4].toInt() and 0xFF) shl 8)
        val calories = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8)
        return DecodedResult.Activity(seq, activityCount, distance, calories)
    }

    private fun parseAlarmQueryResponse(data: ByteArray) {
        val payload = data.copyOfRange(5, data.size)
        val records = payload.size / 8
        if (records == 0) return
        val alarms = (0 until records).map { index ->
            val offset = index * 8
            WatchAlarm(
                slot = payload[offset].toInt() and 0xFF,
                enabled = (payload[offset + 1].toInt() and 0xFF) == 1,
                mode = payload[offset + 2].toInt() and 0xFF,
                hour = payload[offset + 3].toInt() and 0xFF,
                minute = payload[offset + 4].toInt() and 0xFF,
                repeatMask = payload[offset + 7].toInt() and 0xFF
            )
        }
        onResult(DecodedResult.AlarmSettings(alarms))
    }

    private fun parseStepGoalResponse(data: ByteArray) {
        val payload = if (data.size > 5) data.copyOfRange(5, data.size) else byteArrayOf()
        val goal = when {
            payload.size >= 4 && payload[0] == 0x00.toByte() -> (payload[1].toInt() and 0xFF) or ((payload[2].toInt() and 0xFF) shl 8)
            payload.size >= 3 && payload[0] == 0x00.toByte() -> ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
            payload.size >= 2 -> ((payload[payload.size - 2].toInt() and 0xFF) shl 8) or (payload[payload.size - 1].toInt() and 0xFF)
            else -> null
        }
        goal?.let { onResult(DecodedResult.StepGoal(it)) }
    }

    private fun parseSleepBoundaryPacket(b: ByteArray) {
        val markers = mutableListOf<Pair<Int, Int>>()
        var offset = 5
        while (offset + 2 < b.size) {
            val stateId = b[offset].toInt() and 0xFF
            val hour = b[offset + 1].toInt() and 0xFF
            val minute = b[offset + 2].toInt() and 0xFF
            if (hour in 0..23 && minute in 0..59) markers.add(normalizeSleepState(stateId) to (hour * 60 + minute))
            offset += 3
        }
        if (markers.size < 2) return

        var lastTotalMinutes = -1
        val adjustedMarkers = markers.map { (stateId, minutesOfDay) ->
            var adjusted = minutesOfDay
            if (lastTotalMinutes != -1) while (adjusted < lastTotalMinutes) adjusted += 1440
            lastTotalMinutes = adjusted
            stateId to adjusted
        }

        val rawSegments = adjustedMarkers.zipWithNext { start, end ->
            SleepSegment(start.second, end.second, start.first, sleepStateLabel(start.first))
        }

        val mergedSegments = rawSegments.fold(mutableListOf<SleepSegment>()) { merged, segment ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.stateId == segment.stateId) {
                merged[merged.lastIndex] = previous.copy(endMinutes = segment.endMinutes, hasInternalMarkers = true)
            } else merged.add(segment)
            merged
        }

        val total = rawSegments.filter { it.stateId in SLEEP_STAGES }.sumOf { it.endMinutes - it.startMinutes }
        val deep = rawSegments.filter { it.stateId == 0x02 }.sumOf { it.endMinutes - it.startMinutes }
        val light = rawSegments.filter { it.stateId == 0x01 }.sumOf { it.endMinutes - it.startMinutes }
        val rem = rawSegments.filter { it.stateId == 0x03 }.sumOf { it.endMinutes - it.startMinutes }
        
        onResult(DecodedResult.SleepBoundaries(mergedSegments, total, deep, light + rem))
    }

    private fun parseDailyTotalsPacket(b: ByteArray) {
        val dayOffset = b[5].toInt() and 0xFF
        if (dayOffset != 0x00) return
        val steps = readUInt24LE(b, 6)
        val distance = readUInt24LE(b, 9)
        val calories = readUInt24LE(b, 12)
        onResult(DecodedResult.DailyTotals(steps, distance, calories))
    }

    private fun parseWatchExerciseSummaryPacket(b: ByteArray) {
        val sportType = b[5].toInt() and 0xFF
        val durationSeconds = readUInt16LE(b, 6)
        val calories = readUInt16LE(b, 8)
        val averageBpm = b[10].toInt() and 0xFF
        val minBpm = b[11].toInt() and 0xFF
        val maxBpm = b[12].toInt() and 0xFF
        val steps = readUInt24LE(b, 13)
        val distance = readUInt24LE(b, 16)
        if (durationSeconds !in 30..86_400) return
        if (calories !in 0..10_000) return
        if (distance !in 0..300_000) return
        onResult(
            DecodedResult.WatchExerciseSummary(
                sportType = sportType,
                durationSeconds = durationSeconds,
                calories = calories,
                averageBpm = averageBpm.takeIf { it in 30..220 },
                minBpm = minBpm.takeIf { it in 30..220 },
                maxBpm = maxBpm.takeIf { it in 30..220 },
                steps = steps,
                distance = distance
            )
        )
    }

    private fun parseHourlyActivityPacket(b: ByteArray) {
        val bucket = b[5].toInt() and 0xFF
        var stepsDown = 0; var stepsUp = 0; var stepsOther = 0
        var offset = 6
        while (offset + 5 < b.size) {
            stepsDown += readUInt16LE(b, offset)
            stepsUp += readUInt16LE(b, offset + 2)
            stepsOther += readUInt16LE(b, offset + 4)
            offset += 6
        }
        onResult(DecodedResult.HourlyActivity(bucket, stepsDown, stepsUp, stepsOther))
    }

    private fun parseDeviceInfoPacket(b: ByteArray) {
        val infoType = b[5].toInt() and 0xFF
        val text = String(b.copyOfRange(6, b.size)).trim()
        if (infoType == 0x00) onResult(DecodedResult.DeviceInfo(text, null))
        else if (infoType == 0x01) onResult(DecodedResult.DeviceInfo(null, text))
    }

    private fun normalizeSleepState(stateId: Int): Int = when (stateId) {
        0x00, 0x01, 0x02, 0x03 -> stateId
        else -> 0x00
    }

    private fun sleepStateLabel(stateId: Int) = when (stateId) {
        0x00 -> "Hereillä"; 0x01 -> "Kevyt"; 0x02 -> "Syvä"; 0x03 -> "REM"; else -> "State $stateId"
    }

    private fun parseStandardHeartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        return if ((data[0].toInt() and 0x01) != 0) (if (data.size < 3) null else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
        else (if (data.size < 2) null else data[1].toInt() and 0xFF)
    }

    private fun extractHeartRateCandidate(data: ByteArray): Int? {
        for (index in 5 until data.size) {
            val candidate = data[index].toInt() and 0xFF
            if (candidate in 30..220) return candidate
        }
        return null
    }

    private fun readUInt16LE(b: ByteArray, offset: Int): Int = (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
    private fun readUInt24LE(b: ByteArray, offset: Int): Int = (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8) or ((b[offset + 2].toInt() and 0xFF) shl 16)

    companion object {
        val UUID_BATTERY = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val UUID_HEART_RATE = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val UUID_FEE1 = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        val UUID_FEA1 = UUID.fromString("0000fea1-0000-1000-8000-00805f9b34fb")
        val UUID_FEE3 = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
    }
}

private class Fee3PacketReassembler {
    private var packet: ByteArray? = null
    private var position = 0

    fun accept(fragment: ByteArray): ByteArray? {
        if (fragment.isEmpty()) return null
        if (fragment.startsWithDaFitHeader()) reset(fragment)

        val current = packet ?: return null
        val copyLength = minOf(fragment.size, current.size - position)
        fragment.copyInto(current, destinationOffset = position, endIndex = copyLength)
        position += copyLength

        return if (position == current.size) current.also { clear() } else null
    }

    private fun reset(firstFragment: ByteArray) {
        clear()
        val packetLength = firstFragment.daFitPacketLength() ?: return
        if (packetLength !in 5..MAX_PACKET_LENGTH) return
        packet = ByteArray(packetLength)
    }

    private fun clear() {
        packet = null
        position = 0
    }

    private fun ByteArray.startsWithDaFitHeader(): Boolean =
        size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xEA.toByte()

    private fun ByteArray.daFitPacketLength(): Int? {
        if (size < 4) return null
        val high = this[2].toInt() and 0xFF
        val low = this[3].toInt() and 0xFF
        return when {
            high == 0x10 -> low
            high >= 0x20 -> ((high - 0x20) shl 8) or low
            else -> null
        }
    }

    companion object {
        private const val MAX_PACKET_LENGTH = 4 * 1024
    }
}
