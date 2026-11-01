package com.filestech.callhalt.ui.stop

import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The words of one STOP screen: where the user is, what just happened, the rule that unmasks it, what to do,
 * and the buttons that can honestly be offered (docs/ARCHITECTURE.md, D-017).
 *
 * The screen also shows in the minutes AFTER a call (PROJET.md, § 3): it must then neither say "you are on the
 * phone" nor offer to hang up a call that is over. Nor can it hang up a VOIP call (`TelecomManager.endCall`
 * ends cellular calls only): a "Hang up" button there would lie.
 */
internal data class StopText(
    @StringRes val situation: Int,
    @StringRes val happened: Int,
    @StringRes val rule: Int,
    @StringRes val advice: Int,
    /** The app can end this call itself: "Hang up" comes first, and "call my bank" hangs up before dialling (D-011). */
    val offerHangUp: Boolean,
    @StringRes val callMyBank: Int,
)

/** [trigger] and [call] are those of the decision ([com.filestech.callhalt.domain.model.Decision.ShowStop]). */
internal fun stopText(trigger: SensitiveActionKind, call: CallSession): StopText {
    val onCall = call.isActive
    val canHangUp = onCall && call.channel == CallChannel.CELLULAR
    // Hanging up does not end a remote-control session: the advice for those triggers is to switch the phone off.
    val remote = trigger == SensitiveActionKind.REMOTE_CONTROL_OPENED || trigger == SensitiveActionKind.REMOTE_CONTROL_INSTALLED
    return StopText(
        situation = if (onCall) R.string.stop_on_call else R.string.stop_call_ended,
        happened = happened(trigger),
        rule = if (remote) R.string.stop_rule_remote else R.string.stop_rule_codes,
        advice = when {
            remote && onCall -> R.string.stop_advice_remote_on_call
            remote -> R.string.stop_advice_remote_after_call
            onCall -> R.string.stop_advice_on_call
            else -> R.string.stop_advice_after_call
        },
        offerHangUp = canHangUp,
        callMyBank = if (canHangUp) R.string.stop_hang_up_and_call_my_bank else R.string.stop_call_my_bank,
    )
}

/** Exhaustive on purpose: a new trigger does not compile until it says what happened. */
@StringRes
private fun happened(trigger: SensitiveActionKind): Int = when (trigger) {
    SensitiveActionKind.CODE_MESSAGE -> R.string.stop_trigger_code
    SensitiveActionKind.BANK_VALIDATION_REQUEST -> R.string.stop_trigger_bank_validation
    SensitiveActionKind.BANK_APP_OPENED -> R.string.stop_trigger_bank_app
    SensitiveActionKind.REMOTE_CONTROL_OPENED -> R.string.stop_trigger_remote_opened
    SensitiveActionKind.REMOTE_CONTROL_INSTALLED -> R.string.stop_trigger_remote_installed
}
