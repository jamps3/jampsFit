package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.database.FoodEntity
import com.labbaslabs.jampsfit.database.HealthEntry
import com.labbaslabs.jampsfit.database.toEvent
import com.labbaslabs.jampsfit.database.toFood
import com.labbaslabs.jampsfit.database.toHealthEntry
import com.labbaslabs.jampsfit.database.toJson
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupJsonTest {
    @Test
    fun healthEntryRoundTripsNullableMetrics() {
        val original = HealthEntry(timestamp = 1234L, heartRate = 88, sleepMinutes = 420, sleepSegmentsJson = "1,2,3,Kevyt")
        val restored = original.toJson().toHealthEntry()
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.heartRate, restored.heartRate)
        assertEquals(original.sleepMinutes, restored.sleepMinutes)
        assertEquals(original.sleepSegmentsJson, restored.sleepSegmentsJson)
    }

    @Test
    fun userDataEntitiesRoundTripWithoutDatabaseIds() {
        val food = FoodEntity(name = "Test", source = "Home", role = "Protein", unitLabel = "g", kcalPerUnit = 2, defaultAmount = 10f, stepSize = 1f)
        val event = EventEntity(name = "Set", startTime = 1234L, endTime = 5678L, stepDelta = 100)
        assertEquals(food.name, food.toJson().toFood().name)
        assertEquals(event.name, event.toJson().toEvent().name)
        assertEquals(0L, food.toJson().toFood().id)
        assertEquals(0L, event.toJson().toEvent().id)
    }
}
