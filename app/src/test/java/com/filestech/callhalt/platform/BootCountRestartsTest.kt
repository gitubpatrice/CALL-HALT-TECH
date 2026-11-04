package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.Uptime
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * The restart memory's logic, on [InMemoryPreferences]: a boot is reported until it is recorded — in doubt too,
 * since reporting the current boot ends only the calls of earlier ones (RestartSource's contract).
 */
class BootCountRestartsTest {

    /** The process started two hours after boot 12. */
    private val clock = FakePhoneClock(Moment(STARTED, Uptime(12, UP)))
    private val restarts = BootCountRestarts(InMemoryPreferences(), clock)

    @Test
    fun `a boot is reported until it is recorded, then no more`() = runTest {
        assertThat(restarts.unreported()?.at).isEqualTo(Moment(STARTED, Uptime(12, UP)))
        // Not recorded yet: the engine did not commit it (a process killed in between) - reported again.
        val restart = checkNotNull(restarts.unreported())
        restart.record()
        assertThat(restarts.unreported()).isNull()
    }

    @Test
    fun `the next boot is reported, with its own time`() = runTest {
        checkNotNull(restarts.unreported()).record()
        val later = Moment(STARTED.plusSeconds(3 * 3_600), Uptime(13, Duration.ofSeconds(40)))
        clock.processStarted = later
        assertThat(restarts.unreported()?.at).isEqualTo(later)
    }

    /**
     * Placed when the process started, not at the uptime's zero: a restart of Android alone leaves the uptime running
     * from the phone's own start, hours back (P-13).
     */
    @Test
    fun `the restart is placed when the process started`() = runTest {
        clock.now = Moment(STARTED.plusSeconds(600), Uptime(12, UP.plusSeconds(600)))
        assertThat(restarts.unreported()?.at?.uptime).isEqualTo(Uptime(12, UP))
    }

    @Test
    fun `a boot Android does not count is not reported - nor is a clock that fails`() = runTest {
        clock.processStarted = Moment(STARTED)
        assertThat(restarts.unreported()).isNull()

        val failing = object : PhoneClock {
            override fun now(): Moment = error("settings provider gone")

            override fun processStarted(): Moment = error("settings provider gone")
        }
        assertThat(BootCountRestarts(InMemoryPreferences(), failing).unreported()).isNull()
    }

    /** Staying silent would leave the cut call "in progress", and absorb the next real call into it. */
    @Test
    fun `a memory that fails reports the boot, and recording it does not throw`() = runTest {
        val restart = checkNotNull(BootCountRestarts(FailingPreferences(IOException("disk unavailable")), clock).unreported())
        assertThat(restart.at.wall).isEqualTo(STARTED)
        restart.record()
    }

    private companion object {
        val STARTED: Instant = Instant.parse("2026-11-02T13:52:00Z")
        val UP: Duration = Duration.ofHours(2)
    }
}
