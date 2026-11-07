package com.filestech.callhalt.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filestech.callhalt.data.guardian.Guardian
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
            SettingsMapping.GUARDIAN_NUMBER to "06 12 34 56 78",
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
            longPreferencesKey("guardian_number") to 612345678L,
        )

        assertThat(SettingsMapping.fromPreferences(preferences)).isEqualTo(ProtectionSettings())
    }

    @Test
    fun `the relative is read with the name the user knows them by`() {
        val preferences = preferencesOf(
            SettingsMapping.GUARDIAN_NUMBER to "+33 6 12 34 56 78",
            SettingsMapping.GUARDIAN_NAME to "Julie",
        )

        assertThat(SettingsMapping.guardian(preferences)).isEqualTo(Guardian("+33612345678", "Julie"))
        assertThat(SettingsMapping.guardian(preferencesOf(SettingsMapping.GUARDIAN_NAME to "Julie"))).isNull()
    }

    /** No number the app may text, nothing to tell: the engine decides no text a relative would never get. */
    @Test
    fun `a stored number the app may not text is no relative`() {
        val preferences = preferencesOf(SettingsMapping.GUARDIAN_NUMBER to "36111")

        assertThat(SettingsMapping.guardian(preferences)).isNull()
        assertThat(SettingsMapping.fromPreferences(preferences).guardianAlerts).isFalse()
    }
}
