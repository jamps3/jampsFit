package com.labbaslabs.jampsfit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.createBitmap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HealthIconSettings(
    val heartRate: Boolean = true,
    val bloodPressure: Boolean = true,
    val splitBloodPressure: Boolean = false,
    val hrLow: Int = 60,
    val hrHigh: Int = 100,
    val sysLow: Int = 90,
    val sysHigh: Int = 140,
    val diaLow: Int = 60,
    val diaHigh: Int = 90,
) {
    fun save(prefs: SharedPreferences) = prefs.edit {
        putBoolean("iconHr", heartRate)
        putBoolean("iconBp", bloodPressure)
        putBoolean("iconSplitBp", splitBloodPressure)
        listOf(hrLow, hrHigh, sysLow, sysHigh, diaLow, diaHigh).forEachIndexed { i, value -> putInt("iconLimit$i", value) }
    }

    companion object {
        fun load(prefs: SharedPreferences): HealthIconSettings = HealthIconSettings(
            prefs.getBoolean("iconHr", true), prefs.getBoolean("iconBp", true), prefs.getBoolean("iconSplitBp", false),
            prefs.getInt("iconLimit0", 60), prefs.getInt("iconLimit1", 100),
            prefs.getInt("iconLimit2", 90), prefs.getInt("iconLimit3", 140),
            prefs.getInt("iconLimit4", 60), prefs.getInt("iconLimit5", 90),
        )
    }
}

/** Each metric is its own summary so SystemUI doesn't hide it as a group child. */
class HealthStatusNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val previous = mutableMapOf<Int, String>()
    private val channel = "health_status_numbers"

    init {
        clear()
        manager.createNotificationChannel(NotificationChannel(channel, "Health status numbers", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Silent HR and BP numbers in the status bar"
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun update(state: WatchState) {
        val settings = state.statusIcons
        val hr = state.heartRate?.takeIf { it > 0 }?.let { it to state.heartRateMeasuredAt }
            ?: state.heartRateHistory.maxByOrNull { it.timestamp }?.let { it.value to it.timestamp }
        if (settings.heartRate && hr != null) {
            show(101, "Heart rate", listOf(hr.first), listOf(classify(hr.first, settings.hrLow, settings.hrHigh)), hr.second, state.isConnected, "bpm")
        } else cancel(101)
        val bp = state.bpHistory.filter { (it.systolic ?: 0) > 0 && (it.diastolic ?: 0) > 0 }.maxByOrNull { it.timestamp }
        if (settings.bloodPressure && bp != null) {
            val sys = requireNotNull(bp.systolic)
            val dia = requireNotNull(bp.diastolic)
            val levels = listOf(classify(sys, settings.sysLow, settings.sysHigh), classify(dia, settings.diaLow, settings.diaHigh))
            if (settings.splitBloodPressure) {
                cancel(102)
                show(103, "BP systolic", listOf(sys), listOf(levels[0]), bp.timestamp, state.isConnected, "mmHg")
                show(104, "BP diastolic", listOf(dia), listOf(levels[1]), bp.timestamp, state.isConnected, "mmHg")
            } else {
                cancel(103)
                cancel(104)
                show(102, "Blood pressure · systolic / diastolic", listOf(sys, dia), levels, bp.timestamp, state.isConnected, "mmHg")
            }
        } else listOf(102, 103, 104).forEach(::cancel)
    }

    private fun classify(value: Int, low: Int, high: Int): String = when {
        value < low -> "Low"
        value > high -> "High"
        else -> "Within range"
    }

    private fun show(id: Int, title: String, values: List<Int>, levels: List<String>, timestamp: Long?, connected: Boolean, unit: String) {
        val stale = timestamp == null || System.currentTimeMillis() - timestamp > 60 * 60_000L
        val time = timestamp?.let { DateTimeFormatter.ofPattern("dd/MM HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "Unknown time"
        val detail = "${values.joinToString("/")} $unit · ${levels.joinToString(" / ")}\n$time${if (stale) " · Stale" else ""}${if (!connected) " · Disconnected" else ""}"
        if (previous[id] == detail) return
        val bitmap = createBitmap(96, 96)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = if (values.size == 2) 45f else 59f
        }
        values.forEachIndexed { index, value ->
            val marker = when (levels[index]) { "Low" -> "↓"; "High" -> "↑"; else -> "" }
            val label = "$value$marker"
            val originalSize = paint.textSize
            if (paint.measureText(label) > 94f) paint.textSize *= 94f / paint.measureText(label)
            val center = if (values.size == 2) 24f + index * 48f else 48f
            canvas.drawText(label, 48f, center - (paint.ascent() + paint.descent()) / 2, paint)
            paint.textSize = originalSize
        }
        val color = when {
            stale || !connected -> Color.GRAY
            "High" in levels -> Color.rgb(211, 47, 47)
            "Low" in levels -> Color.rgb(25, 118, 210)
            else -> Color.rgb(46, 125, 50)
        }
        val intent = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(context, channel)
            .setSmallIcon(IconCompat.createWithBitmap(bitmap))
            .setContentTitle(title).setContentText(detail.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setColor(color).setContentIntent(intent).setOngoing(true)
            // setSilent(true) puts ungrouped notifications into a shared "silent" group.
            // Status bars can hide child icons, even when silent icons are enabled.
            // The channel already disables sound and vibration; use one summary per metric.
            .setGroup("health_status_$id").setGroupSummary(true)
            .setOnlyAlertOnce(true).setShowWhen(false).build())
        previous[id] = detail
    }

    private fun cancel(id: Int) {
        if (previous.remove(id) != null) manager.cancel(id)
    }

    fun clear() = (101..104).forEach { manager.cancel(it) }.also { previous.clear() }
}
