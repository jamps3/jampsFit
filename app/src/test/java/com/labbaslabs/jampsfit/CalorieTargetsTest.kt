package com.labbaslabs.jampsfit

import com.labbaslabs.jampsfit.food.CalorieTargetMode
import com.labbaslabs.jampsfit.food.calculateBasalCalories
import com.labbaslabs.jampsfit.food.calculateEatCalorieTarget
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieTargetsTest {
    @Test
    fun activeTargetUsesWatchCaloriesOnly() {
        val target = calculateEatCalorieTarget(
            state = WatchState(calories = 420),
            mode = CalorieTargetMode.ActiveBurned,
            nowMillis = localTodayAt(hour = 12)
        )

        assertEquals(420, target)
    }

    @Test
    fun totalTargetAddsBasalBurnSoFar() {
        val target = calculateEatCalorieTarget(
            state = WatchState(
                calories = 300,
                profileGender = "Male",
                profileHeightCm = 168,
                profileWeightKg = 83f,
                profileAgeYears = 41
            ),
            mode = CalorieTargetMode.TotalSoFar,
            nowMillis = localTodayAt(hour = 12)
        )

        assertEquals(1_140, target)
    }

    @Test
    fun missingWatchCaloriesFallbackToZeroActiveCalories() {
        val target = calculateEatCalorieTarget(
            state = WatchState(),
            mode = CalorieTargetMode.ActiveBurned,
            nowMillis = localTodayAt(hour = 12)
        )

        assertEquals(0, target)
    }

    @Test
    fun calorieBaselineResetsActiveCalories() {
        val target = calculateEatCalorieTarget(
            state = WatchState(calories = 900, calorieBaseline = 350),
            mode = CalorieTargetMode.ActiveBurned,
            nowMillis = localTodayAt(hour = 12)
        )

        assertEquals(550, target)
    }

    @Test
    fun basalCaloriesUseProfileInputs() {
        assertEquals(1_680, calculateBasalCalories("Male", 168, 83f, 41))
        assertEquals(1_514, calculateBasalCalories("Female", 168, 83f, 41))
    }

    private fun localTodayAt(hour: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
