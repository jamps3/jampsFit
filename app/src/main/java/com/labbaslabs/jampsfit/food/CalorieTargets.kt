package com.labbaslabs.jampsfit.food

import com.labbaslabs.jampsfit.WatchState
import java.util.Calendar
import kotlin.math.roundToInt

enum class CalorieTargetMode {
    TotalSoFar,
    ActiveBurned
}

fun calculateBasalCalories(state: WatchState): Int {
    return calculateBasalCalories(
        gender = state.profileGender,
        heightCm = state.profileHeightCm,
        weightKg = state.profileWeightKg,
        ageYears = state.profileAgeYears
    )
}

fun calculateBasalCalories(gender: String, heightCm: Int, weightKg: Float, ageYears: Int): Int {
    val genderOffset = if (gender.equals("Female", ignoreCase = true)) -161.0 else 5.0
    return (10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + genderOffset).roundToInt()
}

fun calculateBasalCaloriesSoFar(dailyBasalCalories: Int, nowMillis: Long = System.currentTimeMillis()): Int {
    return (dailyBasalCalories * dayProgress(nowMillis)).roundToInt()
}

fun calculateEatCalorieTarget(
    state: WatchState,
    mode: CalorieTargetMode,
    nowMillis: Long = System.currentTimeMillis()
): Int {
    val activeCalories = state.calories ?: state.caloriesHistory.maxOfOrNull { it.value } ?: 0
    return when (mode) {
        CalorieTargetMode.ActiveBurned -> activeCalories
        CalorieTargetMode.TotalSoFar -> activeCalories + calculateBasalCaloriesSoFar(
            dailyBasalCalories = calculateBasalCalories(state),
            nowMillis = nowMillis
        )
    }.coerceAtLeast(0)
}

fun dayProgress(timestamp: Long): Float {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val millisecond = calendar.get(Calendar.MILLISECOND)
    val elapsed = (((hour * 60L + minute) * 60L + second) * 1000L + millisecond).toFloat()
    return (elapsed / (24f * 60f * 60f * 1000f)).coerceIn(0f, 1f)
}
