package com.labbaslabs.jampsfit

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.labbaslabs.jampsfit.database.HealthEntry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HealthStatusNotificationsTest {
    @Test fun numericIconsRemainSeparateGroupSummariesWithoutSound() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = HealthStatusNotifications(context)
        val now = System.currentTimeMillis()
        try {
            val state = WatchState(
                heartRate = 72,
                heartRateMeasuredAt = now,
                isConnected = true,
                bpHistory = listOf(HealthEntry(timestamp = now, systolic = 120, diastolic = 80)),
            )
            notifications.update(state)
            verifyIcons(manager, setOf(101, 102))
            // Save visual evidence from the emulator after status bar animations settle.
            SystemClock.sleep(1_000L)
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(context.getExternalFilesDir(null), "health-status-icons.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                screenshot.recycle()
            }
            notifications.update(state.copy(statusIcons = state.statusIcons.copy(splitBloodPressure = true)))
            verifyIcons(manager, setOf(101, 103, 104))
            notifications.update(state.copy(statusIcons = state.statusIcons.copy(heartRate = false, bloodPressure = false)))
            verifyIcons(manager, emptySet())
        } finally {
            notifications.clear()
        }
    }

    private fun verifyIcons(manager: NotificationManager, expectedIds: Set<Int>) {
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        var posted = manager.activeNotifications.filter { it.id in 101..104 }
        while (posted.map { it.id }.toSet() != expectedIds && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50L)
            posted = manager.activeNotifications.filter { it.id in 101..104 }
        }
        assertEquals(expectedIds, posted.map { it.id }.toSet())
        assertEquals(posted.size, posted.map { it.notification.group }.distinct().size)
        posted.forEach { status ->
            val notification = status.notification
            assertEquals("health_status_${status.id}", notification.group)
            assertTrue(notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
            assertEquals(Icon.TYPE_BITMAP, notification.smallIcon.type)
            val channel = manager.getNotificationChannel(notification.channelId)
            assertNull(channel.sound)
            assertFalse(channel.shouldVibrate())
        }
    }
}
