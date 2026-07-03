package com.labbaslabs.jampsfit

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmSyncAssistantTest {
    private val assistant = AlarmSyncAssistant()

    @Test
    fun parsesUpcomingClockAlarmWithDate() {
        val parsed = assistant.parseNotification(
            pkg = "com.google.android.deskclock",
            title = "Upcoming alarm",
            text = "ma 15.07 7:30",
            category = Notification.CATEGORY_ALARM,
        )

        requireNotNull(parsed)
        assertEquals(7, parsed.hour)
        assertEquals(30, parsed.minute)
        assertEquals(7, parsed.month)
        assertEquals(15, parsed.day)
        assertEquals(false, parsed.isFiring)
    }

    @Test
    fun detectsFiringAlarmWithoutTime() {
        val parsed = assistant.parseNotification(
            pkg = "com.sec.android.app.clockpackage",
            title = "Alarm",
            text = "Snooze or stop",
            category = Notification.CATEGORY_ALARM,
        )

        requireNotNull(parsed)
        assertEquals(-1, parsed.hour)
        assertEquals(true, parsed.isFiring)
    }

    @Test
    fun ignoresNonAlarmNotification() {
        val parsed = assistant.parseNotification(
            pkg = "com.example.mail",
            title = "Inbox",
            text = "Hello",
            category = "email",
        )

        assertNull(parsed)
    }
}
