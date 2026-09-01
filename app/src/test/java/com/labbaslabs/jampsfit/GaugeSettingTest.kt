package com.labbaslabs.jampsfit

import org.junit.Assert.assertEquals
import org.junit.Test

class GaugeSettingTest {
    @Test
    fun loadsDefaultsAndPersistedOverrides() {
        val settings = loadGaugeSettings(
            mapOf(
                GaugeSetting.BATTERY.preferenceKey to 88,
                GaugeSetting.HEART_RATE.preferenceKey to 92,
            ),
        )

        assertEquals(88, settings[GaugeSetting.BATTERY])
        assertEquals(92, settings[GaugeSetting.HEART_RATE])
        assertEquals(GaugeSetting.STEPS.defaultValue, settings[GaugeSetting.STEPS])
        assertEquals(GaugeSetting.BATTERY_LOW.defaultValue, settings[GaugeSetting.BATTERY_LOW])
    }
}
