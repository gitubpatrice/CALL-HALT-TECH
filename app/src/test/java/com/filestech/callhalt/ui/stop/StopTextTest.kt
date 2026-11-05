package com.filestech.callhalt.ui.stop

import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/** The STOP screen says what is true: where the user is, what just happened, the rule that matches it, what to do. */
class StopTextTest {

    private val remote = setOf(
        SensitiveActionKind.REMOTE_CONTROL_OPENED,
        SensitiveActionKind.REMOTE_CONTROL_INSTALLED,
        SensitiveActionKind.ACCESSIBILITY_ENABLED,
    )

    @Test
    fun `during a cellular call the app may end, it says so and offers to hang up first`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val text = stopText(trigger, onCall = true, CallChannel.CELLULAR, canEndCall = true)
            assertWithMessage(trigger.name).that(text.situation).isEqualTo(R.string.stop_on_call)
            assertWithMessage(trigger.name).that(text.offerHangUp).isTrue()
            assertWithMessage(trigger.name).that(text.callMyBank).isEqualTo(R.string.stop_hang_up_and_call_my_bank)
        }
    }

    /** Whatever the channel and the permission: once the call is over, calling the bank is safe. */
    @Test
    fun `after the call, it never says you are on the phone, nor offers to hang up, and offers to call the bank`() {
        val cases = listOf(CallChannel.CELLULAR to true, CallChannel.CELLULAR to false, CallChannel.VOIP to true, CallChannel.VOIP to false)
        SensitiveActionKind.entries.forEach { trigger ->
            cases.forEach { (channel, permission) ->
                val text = stopText(trigger, onCall = false, channel, canEndCall = permission)
                val case = "$trigger, $channel, permission $permission"
                assertWithMessage(case).that(text.situation).isEqualTo(R.string.stop_call_ended)
                assertWithMessage(case).that(text.offerHangUp).isFalse()
                assertWithMessage(case).that(text.callMyBank).isEqualTo(R.string.stop_call_my_bank)
            }
        }
    }

    /**
     * A WhatsApp call (`endCall` ends cellular calls only), or any call without the permission: no "Hang up" that
     * would lie, and no "Call my bank" that would put the scammer on hold (D-011). The advice says to hang up.
     */
    @Test
    fun `during a call the app cannot end, it offers neither to hang up nor to call the bank`() {
        val cannotEnd = listOf(CallChannel.VOIP to true, CallChannel.CELLULAR to false)
        SensitiveActionKind.entries.forEach { trigger ->
            cannotEnd.forEach { (channel, permission) ->
                val text = stopText(trigger, onCall = true, channel, canEndCall = permission)
                val case = "$trigger, $channel, permission $permission"
                assertWithMessage(case).that(text.situation).isEqualTo(R.string.stop_on_call)
                assertWithMessage(case).that(text.offerHangUp).isFalse()
                assertWithMessage(case).that(text.callMyBank).isNull()
            }
        }
    }

    @Test
    fun `each trigger says what just happened in its own words, whatever the state of the call`() {
        val during = SensitiveActionKind.entries.map { trigger ->
            stopText(trigger, onCall = true, CallChannel.CELLULAR, canEndCall = true).happened
        }
        val after = SensitiveActionKind.entries.map { trigger ->
            stopText(trigger, onCall = false, CallChannel.VOIP, canEndCall = false).happened
        }
        assertThat(during).containsNoDuplicates()
        assertThat(after).isEqualTo(during)
    }

    @Test
    fun `the rule matches the trigger`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val expected = when (trigger) {
                SensitiveActionKind.ACCESSIBILITY_ENABLED -> R.string.stop_rule_accessibility
                in remote -> R.string.stop_rule_remote
                else -> R.string.stop_rule_codes
            }
            assertWithMessage(trigger.name).that(stopText(trigger, onCall = true, CallChannel.CELLULAR, canEndCall = true).rule)
                .isEqualTo(expected)
        }
    }

    /** Hanging up does not end a remote-control session: the phone must be switched off. */
    @Test
    fun `the advice follows the trigger and the state of the call`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val (during, after) = if (trigger in remote) {
                R.string.stop_advice_remote_on_call to R.string.stop_advice_remote_after_call
            } else {
                R.string.stop_advice_on_call to R.string.stop_advice_after_call
            }
            assertWithMessage(trigger.name).that(stopText(trigger, onCall = true, CallChannel.CELLULAR, canEndCall = true).advice)
                .isEqualTo(during)
            assertWithMessage(trigger.name).that(stopText(trigger, onCall = false, CallChannel.CELLULAR, canEndCall = true).advice)
                .isEqualTo(after)
        }
    }
}
