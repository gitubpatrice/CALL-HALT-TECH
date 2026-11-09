package com.filestech.callhalt.ui.stop

import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
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

    private val access = SensitiveActionKind.ACCESSIBILITY_ENABLED

    /** Each button ends the call first; after an accessibility access, removing it replaces the plain "Hang up" (D-033). */
    @Test
    fun `during a cellular call the app may end, it says so and every button hangs up first`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val text = stopText(trigger, onCall = true, CallChannel.CELLULAR, canEndCall = true)
            val first = if (trigger == access) {
                StopButton(R.string.stop_hang_up_and_remove_access, StopChoice.REMOVE_ACCESS)
            } else {
                StopButton(R.string.stop_hang_up, StopChoice.HANG_UP)
            }
            assertWithMessage(trigger.name).that(text.situation).isEqualTo(R.string.stop_on_call)
            assertWithMessage(trigger.name).that(text.buttons)
                .containsExactly(first, StopButton(R.string.stop_hang_up_and_call_my_bank, StopChoice.CALL_MY_BANK))
                .inOrder()
        }
    }

    /** Whatever the channel and the permission: once the call is over, calling the bank, or the settings, is safe. */
    @Test
    fun `after the call, it never says you are on the phone, nor offers to hang up, and offers to call the bank`() {
        val cases = listOf(CallChannel.CELLULAR to true, CallChannel.CELLULAR to false, CallChannel.VOIP to true, CallChannel.VOIP to false)
        SensitiveActionKind.entries.forEach { trigger ->
            cases.forEach { (channel, permission) ->
                val text = stopText(trigger, onCall = false, channel, canEndCall = permission)
                val case = "$trigger, $channel, permission $permission"
                val callMyBank = StopButton(R.string.stop_call_my_bank, StopChoice.CALL_MY_BANK)
                val expected = if (trigger == access) {
                    listOf(StopButton(R.string.stop_remove_access, StopChoice.REMOVE_ACCESS), callMyBank)
                } else {
                    listOf(callMyBank)
                }
                assertWithMessage(case).that(text.situation).isEqualTo(R.string.stop_call_ended)
                assertWithMessage(case).that(text.buttons).containsExactlyElementsIn(expected).inOrder()
            }
        }
    }

    /**
     * A WhatsApp call (`endCall` ends cellular calls only), or any call without the permission: no "Hang up" that
     * would lie, and no "Call my bank" nor "Remove the access" that would leave the scammer on the line (D-011). The
     * advice says to hang up.
     */
    @Test
    fun `during a call the app cannot end, it offers no button but Continue`() {
        val cannotEnd = listOf(CallChannel.VOIP to true, CallChannel.CELLULAR to false)
        SensitiveActionKind.entries.forEach { trigger ->
            cannotEnd.forEach { (channel, permission) ->
                val text = stopText(trigger, onCall = true, channel, canEndCall = permission)
                val case = "$trigger, $channel, permission $permission"
                assertWithMessage(case).that(text.situation).isEqualTo(R.string.stop_on_call)
                assertWithMessage(case).that(text.buttons).isEmpty()
            }
        }
    }

    /**
     * After a remote-control app was installed, the screen that knows it offers to uninstall it, by name, first — the
     * call ending first, as for the accessibility (FN-3, D-039); during a call the app cannot end, nothing (D-011).
     */
    @Test
    fun `the install STOP that knows the app offers to uninstall it first, the call ending first`() {
        val installed = SensitiveActionKind.REMOTE_CONTROL_INSTALLED
        val during = stopText(installed, onCall = true, CallChannel.CELLULAR, canEndCall = true, installedApp = "AnyDesk")
        val after = stopText(installed, onCall = false, CallChannel.CELLULAR, canEndCall = true, installedApp = "AnyDesk")
        val cannotEnd = stopText(installed, onCall = true, CallChannel.VOIP, canEndCall = true, installedApp = "AnyDesk")

        assertThat(during.buttons).containsExactly(
            StopButton(R.string.stop_hang_up_and_uninstall, StopChoice.UNINSTALL, "AnyDesk"),
            StopButton(R.string.stop_hang_up_and_call_my_bank, StopChoice.CALL_MY_BANK),
        ).inOrder()
        assertThat(after.buttons).containsExactly(
            StopButton(R.string.stop_uninstall, StopChoice.UNINSTALL, "AnyDesk"),
            StopButton(R.string.stop_call_my_bank, StopChoice.CALL_MY_BANK),
        ).inOrder()
        assertThat(cannotEnd.buttons).isEmpty()
    }

    /** The app installed during the call, now at work: the screen speaks of it at work, and still offers to uninstall it. */
    @Test
    fun `the STOP of the installed app at work offers to uninstall it too`() {
        val text = stopText(SensitiveActionKind.REMOTE_CONTROL_OPENED, onCall = false, CallChannel.CELLULAR, canEndCall = true, "AnyDesk")
        assertThat(text.buttons.first()).isEqualTo(StopButton(R.string.stop_uninstall, StopChoice.UNINSTALL, "AnyDesk"))
    }

    /** An app's name offered for any other trigger is ignored: only an installation is undone by uninstalling. */
    @Test
    fun `no other trigger offers to uninstall`() {
        SensitiveActionKind.entries.filter { it !in UNINSTALL_TRIGGERS }.forEach { trigger ->
            listOf(true, false).forEach { onCall ->
                val text = stopText(trigger, onCall, CallChannel.CELLULAR, canEndCall = true, installedApp = "AnyDesk")
                assertWithMessage("$trigger, on call $onCall").that(text.buttons.map { it.choice })
                    .doesNotContain(StopChoice.UNINSTALL)
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

    /**
     * Hanging up does not end a remote-control session: the phone must be switched off. An accessibility access
     * outlives the restart: it is removed first (D-033).
     */
    @Test
    fun `the advice follows the trigger and the state of the call`() {
        SensitiveActionKind.entries.forEach { trigger ->
            val (during, after) = when (trigger) {
                access -> R.string.stop_advice_accessibility_on_call to R.string.stop_advice_accessibility_after_call
                in remote -> R.string.stop_advice_remote_on_call to R.string.stop_advice_remote_after_call
                else -> R.string.stop_advice_on_call to R.string.stop_advice_after_call
            }
            assertWithMessage(trigger.name).that(stopText(trigger, onCall = true, CallChannel.CELLULAR, canEndCall = true).advice)
                .isEqualTo(during)
            assertWithMessage(trigger.name).that(stopText(trigger, onCall = true, CallChannel.VOIP, canEndCall = true).advice)
                .isEqualTo(during)
            assertWithMessage(trigger.name).that(stopText(trigger, onCall = false, CallChannel.CELLULAR, canEndCall = true).advice)
                .isEqualTo(after)
        }
    }
}
