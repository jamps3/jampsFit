package com.labbaslabs.jampsfit

import android.app.Notification
import android.util.Log

class AlarmSyncAssistant {
    
    data class ParsedAlarm(val hour: Int, val minute: Int, val month: Int, val day: Int, val isFiring: Boolean)

    fun parseNotification(pkg: String, title: String, text: String, category: String): ParsedAlarm? {
        val isAlarmPkg = pkg.contains("clock") || pkg.contains("alarm") || 
                        category == Notification.CATEGORY_ALARM ||
                        pkg == "com.sec.android.app.clockpackage" ||
                        pkg == "com.oneplus.deskclock" ||
                        pkg == "com.coloros.alarmclock" ||
                        pkg == "com.oppo.alarmclock"
        
        if (!isAlarmPkg) return null

        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()
        val isUpcoming = lowerText.contains("tuleva") || lowerText.contains("upcoming") || 
                        lowerTitle.contains("tuleva") || lowerTitle.contains("upcoming")

        val isFiring = !isUpcoming && (lowerText.contains("lopeta") || lowerText.contains("stop") || 
                       lowerText.contains("dismiss") || lowerText.contains("snooze") || 
                       lowerText.contains("torkku") || lowerTitle.contains("torkku"))

        if ((pkg == "com.google.android.deskclock" || pkg == "com.android.deskclock")) {
            try {
                val colonTimeRegex = Regex("([0-1]?[0-9]|2[0-3]):([0-5][0-9])")
                val dotTimeRegex = Regex("([0-1]?[0-9]|2[0-3])[.]([0-5][0-9])")
                val match = colonTimeRegex.find(text)
                    ?: colonTimeRegex.find(title)
                    ?: dotTimeRegex.find(text)
                    ?: dotTimeRegex.find(title)
                
                if (match != null) {
                    var hour = match.groupValues[1].toInt()
                    val minute = match.groupValues[2].toInt()
                    val combined = "$lowerTitle $lowerText"
                    if (combined.contains("pm") && (hour < 12)) hour += 12
                    if (combined.contains("am") && (hour == 12)) hour = 0
                    
                    var month = 0
                    var day = 0
                    val dateRegex = Regex("(?:ma|ti|ke|to|pe|la|su)\\s+([0-3]?[0-9])[.]([0-1]?[0-9])")
                    val dateMatch = dateRegex.find(text) ?: dateRegex.find(title)
                    if (dateMatch != null) {
                        day = dateMatch.groupValues[1].toInt()
                        month = dateMatch.groupValues[2].toInt()
                    }
                    return ParsedAlarm(hour, minute, month, day, isFiring)
                }
            } catch (e: Exception) {
                Log.e("AlarmSyncAssistant", "Error parsing: ${e.message}")
            }
        }
        
        if (isFiring) return ParsedAlarm(-1, -1, 0, 0, true) // Marker for firing without time
        
        return null
    }
}
