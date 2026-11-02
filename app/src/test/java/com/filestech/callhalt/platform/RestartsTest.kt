package com.filestech.callhalt.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.filestech.callhalt.platform.telephony.InMemoryPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant

/**
 * The restart memory's logic, on [InMemoryPreferences]: a boot is reported until it is recorded — in doubt too,
 * since reporting the current boot ends only what started before it (RestartSource's contract).
 */
class RestartsTest {

    private var boot: Boot? = Boot(count = 12, at = BOOTED)
    private val restarts = BootCountRestarts(InMemoryPreferences()) { boot }

    @Test
    fun `a boot is reported until it is recorded, then no more`() = runTest {
        assertThat(restarts.unreported()?.at).isEqualTo(BOOTED)
        // Not recorded yet: the engine did not commit it (a process killed in between) - reported again.
        val restart = checkNotNull(restarts.unreported())
        restart.record()
        assertThat(restarts.unreported()).isNull()
    }

    @Test
    fun `the next boot is reported, with its own time`() = runTest {
        checkNotNull(restarts.unreported()).record()
        val later = BOOTED.plusSeconds(3_600)
        boot = Boot(count = 13, at = later)
        assertThat(restarts.unreported()?.at).isEqualTo(later)
    }

    @Test
    fun `a boot Android does not give is not reported - nor is a reader that fails`() = runTest {
        boot = null
        assertThat(restarts.unreported()).isNull()
        assertThat(BootCountRestarts(InMemoryPreferences()) { error("settings provider gone") }.unreported()).isNull()
    }

    /** Staying silent would leave the cut call "in progress", and absorb the next real call into it. */
    @Test
    fun `a memory that fails reports the boot, and recording it does not throw`() = runTest {
        val restart = checkNotNull(BootCountRestarts(FailingStore(IOException("disk unavailable"))) { boot }.unreported())
        assertThat(restart.at).isEqualTo(BOOTED)
        restart.record()
    }

    private class FailingStore(private val failure: Exception) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw failure
    }

    private companion object {
        val BOOTED: Instant = Instant.parse("2026-11-02T13:52:00Z")
    }
}
