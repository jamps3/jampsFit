package com.labbaslabs.jampsfit

enum class GaugeSetting(
    val preferenceKey: String,
    val defaultValue: Int,
) {
    BATTERY_LOW("gaugeBatteryLow", 0),
    BATTERY("gaugeBattery", 100),
    ACTIVITY("gaugeActivity", 100),
    STEPS("gaugeSteps", 10_000),
    HEART_RATE("gaugeHeartRate", 75),
    SPO2("gaugeSpo2", 98),
    BLOOD_PRESSURE_SYSTOLIC("gaugeBloodPressureSystolic", 120),
    BLOOD_PRESSURE_DIASTOLIC("gaugeBloodPressureDiastolic", 80),
    DISTANCE("gaugeDistance", 5_000),
    CALORIES_BURNED("gaugeCaloriesBurned", 500),
    TOTAL_CALORIES("gaugeTotalCalories", 2_500),
}

fun loadGaugeSettings(values: Map<String, *>): Map<GaugeSetting, Int> =
    GaugeSetting.entries.associateWith { setting ->
        (values[setting.preferenceKey] as? Int) ?: setting.defaultValue
    }
