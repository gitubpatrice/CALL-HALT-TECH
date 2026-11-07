package com.filestech.callhalt.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filestech.callhalt.data.guardian.GuardianRepository
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.platform.FailingPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration

/** The port's contract (SettingsSource): it never throws, and in doubt answers with the protective defaults. */
class SettingsRepositoryTest {

    @Test
    fun `a file the disk cannot read gives the protective defaults`() = runTest {
        assertThat(SettingsRepository(FailingPreferences(IOException("disk unavailable"))).current())
            .isEqualTo(ProtectionSettings())
    }

    /** Coherence audit of 2026-11-07, motif 12: thrown, it reached the engine and lost the signal — the STOP. */
    @Test
    fun `any other failure gives the protective defaults too`() = runTest {
        assertThat(SettingsRepository(FailingPreferences(IllegalStateException("two stores on one file"))).current())
            .isEqualTo(ProtectionSettings())
    }

    /**
     * GPT review of 2026-11-07, 1, which said it would throw: a value kept under another type (an older version, a
     * migration) is read as absent (`SettingsMapping`'s own `read`), so the protective defaults, and no relative.
     */
    @Test
    fun `a value kept under another type reads as absent, so the protective defaults and no relative`() = runTest {
        val wrongTypes = storeOf(
            mutablePreferencesOf(
                stringPreferencesKey("protection_enabled") to "yes",
                intPreferencesKey("guardian_number") to 612345678,
            ),
        )

        assertThat(SettingsRepository(wrongTypes).current()).isEqualTo(ProtectionSettings())
        assertThat(GuardianRepository(wrongTypes).current()).isNull()
    }

    private fun storeOf(preferences: Preferences) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(preferences)

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            error("read only")
    }

    /** Review of 2026-11-07, T2: the defaults themselves, written out — the comparisons above would follow a change. */
    @Test
    fun `the defaults protect`() = runTest {
        val read = SettingsRepository(FailingPreferences(IllegalStateException("unreadable"))).current()
        assertThat(read.enabled).isTrue()
        assertThat(read.afterCallWindow).isEqualTo(Duration.ofMinutes(15))
    }
}
