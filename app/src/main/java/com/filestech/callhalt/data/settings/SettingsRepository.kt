package com.filestech.callhalt.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.port.SettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's settings, in DataStore. Read side only for now: the settings screen brings the write side,
 * behind the relative's code once guardian mode exists (PROJET.md, § 4.6).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsSource {

    /** The settings, with the protective defaults whenever the file cannot be read. */
    val settings: Flow<ProtectionSettings> = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error
            Timber.w(error, "Settings unreadable: protective defaults")
            emit(emptyPreferences())
        }
        .map(SettingsMapping::fromPreferences)

    override suspend fun current(): ProtectionSettings = settings.first()
}

/**
 * Preferences → settings, pure and TOTAL: it cannot throw. A stored value outside its range, or of another
 * type under the same name (an older version, a hand-edited file), falls back to the default, which is the
 * protective one: never a crash, never "protection off" — a throw here would stop every signal.
 */
internal object SettingsMapping {
    val ENABLED = booleanPreferencesKey("protection_enabled")
    val AFTER_CALL_WINDOW_MINUTES = intPreferencesKey("after_call_window_minutes")
    val LONG_CALL_THRESHOLD_MINUTES = intPreferencesKey("long_call_threshold_minutes")
    val GUARDIAN_ALERTS = booleanPreferencesKey("guardian_alerts")

    fun fromPreferences(preferences: Preferences): ProtectionSettings = ProtectionSettings(
        enabled = preferences.read(ENABLED) ?: true,
        afterCallWindow = minutesWithin(preferences.read(AFTER_CALL_WINDOW_MINUTES), ProtectionSettings.AFTER_CALL_WINDOW_RANGE)
            ?: ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW,
        longCallThreshold = minutesWithin(preferences.read(LONG_CALL_THRESHOLD_MINUTES), ProtectionSettings.LONG_CALL_THRESHOLD_RANGE)
            ?: ProtectionSettings.DEFAULT_LONG_CALL_THRESHOLD,
        guardianAlerts = preferences.read(GUARDIAN_ALERTS) ?: false,
    )

    /**
     * `preferences[key]` casts without checking, so a value of another type under the key's name throws a
     * ClassCastException where it is USED. Read through the map instead: the wrong type reads as absent.
     */
    private inline fun <reified T : Any> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

    private fun minutesWithin(stored: Int?, range: ClosedRange<Duration>): Duration? =
        stored?.let { Duration.ofMinutes(it.toLong()) }?.takeIf { it in range }
}
