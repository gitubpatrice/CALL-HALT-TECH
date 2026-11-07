package com.filestech.callhalt.data.guardian

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.filestech.callhalt.data.settings.SettingsMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The relative (docs/ARCHITECTURE.md, D-034), kept with the settings — the engine reads it there as "the relative is
 * told" ([SettingsMapping.fromPreferences]). Written only from the app's own screen, by the user.
 */
@Singleton
class GuardianRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** None when the file cannot be read: no number to text, as with none set up. */
    val guardian: Flow<Guardian?> = dataStore.data
        .catch { error ->
            if (error !is IOException) throw error
            Timber.w(error, "Settings unreadable: no relative to text")
            emit(emptyPreferences())
        }
        .map(SettingsMapping::guardian)

    suspend fun current(): Guardian? = guardian.first()

    /** Throws IOException when the file cannot be written: the screen says it, the relative is not set up. */
    suspend fun set(guardian: Guardian) {
        dataStore.edit { preferences ->
            preferences[SettingsMapping.GUARDIAN_NUMBER] = guardian.number
            val name = guardian.name
            if (name.isNullOrBlank()) {
                preferences.remove(SettingsMapping.GUARDIAN_NAME)
            } else {
                preferences[SettingsMapping.GUARDIAN_NAME] = name
            }
        }
    }

    /** Throws IOException when the file cannot be written: the relative is then still the one set up. */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(SettingsMapping.GUARDIAN_NUMBER)
            preferences.remove(SettingsMapping.GUARDIAN_NAME)
        }
    }
}
