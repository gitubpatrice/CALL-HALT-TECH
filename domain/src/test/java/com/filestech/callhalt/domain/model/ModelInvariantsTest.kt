package com.filestech.callhalt.domain.model

import com.filestech.callhalt.domain.detection.DetectorState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

class ModelInvariantsTest {

    private val t0 = Moment(Instant.parse("2026-11-01T10:00:00Z"))

    @Test
    fun `the default settings are the protective ones`() {
        val defaults = ProtectionSettings()

        assertThat(defaults.enabled).isTrue()
        assertThat(defaults.afterCallWindow).isEqualTo(Duration.ofMinutes(15))
        assertThat(defaults.longCallThreshold).isEqualTo(Duration.ofMinutes(10))
        // No relative is set up on a fresh install: nobody to text.
        assertThat(defaults.guardianAlerts).isFalse()
    }

    @Test
    fun `settings refuse values outside their ranges, bounds accepted`() {
        ProtectionSettings(afterCallWindow = Duration.ZERO)
        ProtectionSettings(afterCallWindow = Duration.ofMinutes(60))
        ProtectionSettings(longCallThreshold = Duration.ofMinutes(1))
        ProtectionSettings(longCallThreshold = Duration.ofMinutes(120))

        assertThrows<IllegalArgumentException> { ProtectionSettings(afterCallWindow = Duration.ofMinutes(-1)) }
        assertThrows<IllegalArgumentException> { ProtectionSettings(afterCallWindow = Duration.ofMinutes(61)) }
        assertThrows<IllegalArgumentException> { ProtectionSettings(longCallThreshold = Duration.ofSeconds(59)) }
        assertThrows<IllegalArgumentException> { ProtectionSettings(longCallThreshold = Duration.ofMinutes(121)) }
    }

    @Test
    fun `a call cannot end before it starts`() {
        assertThrows<IllegalArgumentException> {
            CallSession(
                CallChannel.CELLULAR,
                CallDirection.INCOMING,
                Counterpart.Hidden,
                startedAt = t0,
                endedAt = t0.minus(Duration.ofSeconds(1)),
            )
        }
    }

    @Test
    fun `a blank number is refused`() {
        assertThrows<IllegalArgumentException> { Counterpart.Number(" ", inContacts = false) }
    }

    @Test
    fun `a call is filed under its own channel`() {
        val voip = CallSession(CallChannel.VOIP, CallDirection.INCOMING, Counterpart.Unreadable, startedAt = t0)

        assertThrows<IllegalArgumentException> { DetectorState(calls = mapOf(CallChannel.CELLULAR to voip)) }
    }

    @Test
    fun `a long-call reminder belongs to a tracked call`() {
        assertThrows<IllegalArgumentException> { DetectorState(longCallWarned = setOf(CallChannel.VOIP)) }
    }
}
