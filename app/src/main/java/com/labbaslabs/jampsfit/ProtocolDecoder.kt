package com.labbaslabs.jampsfit

import android.util.Log
import com.labbaslabs.jampsfit.database.HealthEntry
import java.util.*

class ProtocolDecoder(private val onResult: (DecodedResult) -> Unit) {

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
        data class PowerSave(val enabled: Boolean) : DecodedResult()
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
                parseFee3Packet(data)
            }
        }
    }

    private fun parseFee3Packet(data: ByteArray) {
        if (data.size < 5 || data[0] != 0xFE.toByte() || data[1] != 0xEA.toByte() || data[2] != 0x20.toByte()) return
        
        when (data[4].toInt() and 0xFF) {
            0x1F -> {
                val value = data.getOrNull(5)?.toInt()?.and(0xFF)
                val interval = when (value) {
                    0x00 -> 0; 0x01 -> 5; 0x02 -> 10; 0x03 -> 15; 0x04 -> 30; 0x05 -> 60; else -> null
                }
                if (interval != null) onResult(DecodedResult.AutoHeartRate(interval))
            }
            0x21 -> parseAlarmQueryResponse(data)
            0x26 -> parseStepGoalResponse(data)
            0x32 -> if (data.size >= 8) parseSleepBoundaryPacket(data)
            0x33 -> if (data.size >= 15) parseDailyTotalsPacket(data)
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
            if (hour in 0..23 && minute in 0..59) markers.add(stateId to (hour * 60 + minute))
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

        val total = rawSegments.filter { it.stateId != 0x00 }.sumOf { it.endMinutes - it.startMinutes }
        val deep = rawSegments.filter { it.stateId == 0x02 }.sumOf { it.endMinutes - it.startMinutes }
        val light = rawSegments.filter { it.stateId == 0x01 || it.stateId == 0x03 }.sumOf { it.endMinutes - it.startMinutes }
        
        onResult(DecodedResult.SleepBoundaries(mergedSegments, total, deep, light))
    }

    private fun parseDailyTotalsPacket(b: ByteArray) {
        val dayOffset = b[5].toInt() and 0xFF
        if (dayOffset != 0x00) return
        val steps = readUInt24LE(b, 6)
        val distance = readUInt24LE(b, 9)
        val calories = readUInt24LE(b, 12)
        onResult(DecodedResult.DailyTotals(steps, distance, calories))
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
        val text = String(b.copyOfRange(6, b.size)).trim { it <= ' ' }
        if (infoType == 0x00) onResult(DecodedResult.DeviceInfo(text, null))
        else if (infoType == 0x01) onResult(DecodedResult.DeviceInfo(null, text))
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
