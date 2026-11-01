package com.filestech.callhalt.ui.stop

import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.time.Instant

/** The STOP screen says what is true: where the user is, what just happened, the rule that matches it, what to do. */
class StopTextTest {

    private val remote = setOf(SensitiveActionKind.REMOTE_CONTROL_OPENED, SensitiveActionKind.REMOTE_CONTROL_INSTALLED)

    @Test
    fun `during a cellular call, the screen says so and offers to hang up first`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val text = stopText(trigger, cellularOnCall)
            assertWithMessage(trigger.name).that(text.situation).isEqualTo(R.string.stop_on_call)
            assertWithMessage(trigger.name).that(text.offerHangUp).isTrue()
            assertWithMessage(trigger.name).that(text.callMyBank).isEqualTo(R.string.stop_hang_up_and_call_my_bank)
        }
    }

    @Test
    fun `after the call, it never says you are on the phone, nor offers to hang up`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val text = stopText(trigger, cellularOnCall.copy(endedAt = START.plusSeconds(60)))
            assertWithMessage(trigger.name).that(text.situation).isEqualTo(R.string.stop_call_ended)
            assertWithMessage(trigger.name).that(text.offerHangUp).isFalse()
            assertWithMessage(trigger.name).that(text.callMyBank).isEqualTo(R.string.stop_call_my_bank)
        }
    }

    /** `TelecomManager.endCall` ends cellular calls only: a "Hang up" button on a WhatsApp call would lie. */
    @Test
    fun `during a VOIP call, it says the user is on the phone but offers no hang-up it cannot do`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val text = stopText(trigger, cellularOnCall.copy(channel = CallChannel.VOIP))
            assertWithMessage(trigger.name).that(text.situation).isEqualTo(R.string.stop_on_call)
            assertWithMessage(trigger.name).that(text.offerHangUp).isFalse()
            assertWithMessage(trigger.name).that(text.callMyBank).isEqualTo(R.string.stop_call_my_bank)
        }
    }

    @Test
    fun `each trigger says what just happened in its own words, whatever the state of the call`() {
        val during = SensitiveActionKind.entries.map { stopText(it, cellularOnCall).happened }
        val after = SensitiveActionKind.entries.map { stopText(it, cellularOnCall.copy(endedAt = START)).happened }
        assertThat(during).containsNoDuplicates()
        assertThat(after).isEqualTo(during)
    }

    @Test
    fun `the rule matches the trigger`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val expected = if (trigger in remote) R.string.stop_rule_remote else R.string.stop_rule_codes
            assertWithMessage(trigger.name).that(stopText(trigger, cellularOnCall).rule).isEqualTo(expected)
        }
    }

    /** Hanging up does not end a remote-control session: the phone must be switched off. */
    @Test
    fun `the advice follows the trigger and the state of the call`() {
        val ended = cellularOnCall.copy(endedAt = START)
        SensitiveActionKind.entries.forEach { trigger ->
            val (during, after) = if (trigger in remote) {
                R.string.stop_advice_remote_on_call to R.string.stop_advice_remote_after_call
            } else {
                R.string.stop_advice_on_call to R.string.stop_advice_after_call
            }
            assertWithMessage(trigger.name).that(stopText(trigger, cellularOnCall).advice).isEqualTo(during)
            assertWithMessage(trigger.name).that(stopText(trigger, ended).advice).isEqualTo(after)
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-01T10:00:00Z")
        val cellularOnCall = CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable, startedAt = START)
    }
}
