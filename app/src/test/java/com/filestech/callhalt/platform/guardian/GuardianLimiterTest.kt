package com.filestech.callhalt.platform.guardian

import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Uptime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** One text per kind of alert in a quarter of an hour (Patrice, 2026-11-07), and a ceiling no fault goes through. */
class GuardianLimiterTest {

    private val limiter = GuardianLimiter()
    private val code = GuardianEvent.StopShown(SensitiveActionKind.CODE_MESSAGE, duringCall = true)

    private fun at(minutes: Long, boot: Int = 1) =
        Moment(T0.plus(Duration.ofMinutes(minutes)), Uptime(boot, Duration.ofHours(1).plusMinutes(minutes)))

    /** As the notifier does: counted once allowed, and handed to the phone. */
    private fun sent(event: GuardianEvent, at: Moment): Boolean {
        val allowed = limiter.allows(event, at)
        if (allowed) limiter.record(event, at)
        return allowed
    }

    @Test
    fun `the same alert again within a quarter of an hour sends no text`() {
        assertThat(sent(code, at(0))).isTrue()
        assertThat(sent(code, at(5))).isFalse()
        // The call ended meanwhile: the same alert still.
        assertThat(sent(code.copy(duringCall = false), at(14))).isFalse()
        assertThat(sent(code, at(15))).isTrue()
    }

    /** A remote-control app after a code is worse news; "Continue" chosen is news of its own. */
    @Test
    fun `another kind of alert sends its own text`() {
        assertThat(sent(code, at(0))).isTrue()
        assertThat(sent(GuardianEvent.StopShown(SensitiveActionKind.REMOTE_CONTROL_INSTALLED, duringCall = true), at(1))).isTrue()
        assertThat(sent(GuardianEvent.StopOverridden(SensitiveActionKind.CODE_MESSAGE, duringCall = true), at(2))).isTrue()
    }

    /**
     * The bank asks three times; the daughter called after the first "Continue" and was told "it is the bank": each
     * "Continue" is a new act, and she learns the validations went on (Patrice, 2026-11-07; faux positifs 3).
     */
    @Test
    fun `each Continue sends its text`() {
        val overridden = GuardianEvent.StopOverridden(SensitiveActionKind.BANK_VALIDATION_REQUEST, duringCall = true)
        listOf(0L, 9L, 14L).forEach { minute -> assertThat(sent(overridden, at(minute))).isTrue() }
    }

    /** The permission taken back, the phone refused it: the next alert must not be held back by a text never sent. */
    @Test
    fun `an alert not recorded holds nothing back`() {
        assertThat(limiter.allows(code, at(0))).isTrue()
        assertThat(sent(code, at(1))).isTrue()
    }

    /** No network at the first STOP: the same alert a minute later, the network back, sends its text. */
    @Test
    fun `a text that did not go holds nothing back`() {
        assertThat(sent(code, at(0))).isTrue()
        limiter.release(code, at(0))
        assertThat(sent(code, at(1))).isTrue()
        // Released, a text recorded since stays recorded.
        limiter.release(code, at(0))
        assertThat(sent(code, at(2))).isFalse()
    }

    @Test
    fun `no more than the ceiling in a day, whatever happens`() {
        val kinds = SensitiveActionKind.entries.flatMap {
            listOf(GuardianEvent.StopShown(it, duringCall = true), GuardianEvent.StopOverridden(it, duringCall = true))
        }
        val sentCount = (0 until 60).count { quarter -> sent(kinds[quarter % kinds.size], at(quarter * 2L)) }
        assertThat(sentCount).isEqualTo(GuardianLimiter.MAX_PER_DAY)
        // A day after the first ones, room again.
        assertThat(sent(code, at(24 * 60L + 1))).isTrue()
    }

    /** 20 minutes on the uptime, 5 on a civil clock set back meanwhile: a second scam, its own text. */
    @Test
    fun `the hold is measured on the phone's uptime, not the civil clock`() {
        val first = at(0)
        assertThat(sent(code, first)).isTrue()
        val later = Moment(first.wall.plus(Duration.ofMinutes(5)), Uptime(1, Duration.ofHours(1).plusMinutes(20)))
        assertThat(sent(code, later)).isTrue()
    }

    /** Two boots compare on the civil clock: one set back across the restart puts the first alert "later", held nothing. */
    @Test
    fun `an alert that looks later than now holds nothing back`() {
        assertThat(sent(code, Moment(T0.plus(Duration.ofMinutes(10)), Uptime(1, Duration.ofHours(1))))).isTrue()
        assertThat(sent(code, Moment(T0.plus(Duration.ofMinutes(2)), Uptime(2, Duration.ofMinutes(1))))).isTrue()
    }

    /** Within the quarter of an hour on the civil clock across a restart: the same scam, already told. */
    @Test
    fun `the same alert just after a restart sends no text`() {
        assertThat(sent(code, at(0, boot = 1))).isTrue()
        assertThat(sent(code, Moment(T0.plus(Duration.ofMinutes(2)), Uptime(2, Duration.ofMinutes(1))))).isFalse()
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-11-07T10:00:00Z")
    }
}
