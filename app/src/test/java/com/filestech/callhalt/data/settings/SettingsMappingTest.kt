package com.filestech.callhalt.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class SettingsMappingTest {

    @Test
    fun `nothing stored reads as the protective defaults`() {
        assertThat(SettingsMapping.fromPreferences(emptyPreferences())).isEqualTo(ProtectionSettings())
    }

    @Test
    fun `stored values are read`() {
        val preferences = preferencesOf(
            SettingsMapping.ENABLED to false,
            SettingsMapping.AFTER_CALL_WINDOW_MINUTES to 30,
            SettingsMapping.LONG_CALL_THRESHOLD_MINUTES to 5,
            SettingsMapping.GUARDIAN_ALERTS to true,
        )

        assertThat(SettingsMapping.fromPreferences(preferences)).isEqualTo(
            ProtectionSettings(
                enabled = false,
                afterCallWindow = Duration.ofMinutes(30),
                longCallThreshold = Duration.ofMinutes(5),
                guardianAlerts = true,
            ),
        )
    }

    @Test
    fun `a value out of range falls back to the default instead of crashing`() {
        val preferences = preferencesOf(
            SettingsMapping.AFTER_CALL_WINDOW_MINUTES to -5,
            SettingsMapping.LONG_CALL_THRESHOLD_MINUTES to 10_000,
        )

        val settings = SettingsMapping.fromPreferences(preferences)

        assertThat(settings.afterCallWindow).isEqualTo(ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW)
        assertThat(settings.longCallThreshold).isEqualTo(ProtectionSettings.DEFAULT_LONG_CALL_THRESHOLD)
    }

    @Test
    fun `a value of another type under a known name reads as the default instead of throwing`() {
        val preferences = preferencesOf(
            stringPreferencesKey("protection_enabled") to "no",
            stringPreferencesKey("after_call_window_minutes") to "30",
            longPreferencesKey("long_call_threshold_minutes") to 5L,
            intPreferencesKey("guardian_alerts") to 1,
        )

        assertThat(SettingsMapping.fromPreferences(preferences)).isEqualTo(ProtectionSettings())
    }
}
