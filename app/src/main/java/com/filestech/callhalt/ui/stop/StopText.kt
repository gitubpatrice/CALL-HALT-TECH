package com.filestech.callhalt.ui.stop

import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The words of one STOP screen: where the user is, what just happened, the rule that unmasks it, what to do,
 * and the buttons that can honestly be offered (docs/ARCHITECTURE.md, D-017).
 *
 * The screen also shows in the minutes AFTER a call (PROJET.md, § 3): it must then neither say "you are on the
 * phone" nor offer to hang up a call that is over. Nor can it offer to hang up what the app cannot end: a VOIP
 * call (`TelecomManager.endCall` ends cellular calls only), or any call without the permission to answer calls.
 */
internal data class StopText(
    @StringRes val situation: Int,
    @StringRes val happened: Int,
    @StringRes val rule: Int,
    @StringRes val advice: Int,
    /** The app can end this call itself: "Hang up" comes first. */
    val offerHangUp: Boolean,
    /**
     * Null while a call goes on that the app cannot end: dialling then would put the scammer on hold instead of
     * cutting him off (D-011) — the advice says to hang up. Otherwise it hangs up first when it can.
     */
    @StringRes val callMyBank: Int?,
)

/**
 * [onCall] and [channel] are those of the decision's call ([com.filestech.callhalt.domain.model.Decision.ShowStop]);
 * [canEndCall] says whether the app holds the permission to end a call.
 */
internal fun stopText(trigger: SensitiveActionKind, onCall: Boolean, channel: CallChannel, canEndCall: Boolean): StopText {
    val canHangUp = onCall && channel == CallChannel.CELLULAR && canEndCall
    // Hanging up does not end a remote-control session, nor what an app given the accessibility access does: the advice
    // for those triggers is to switch the phone off.
    val remote = trigger in REMOTE_TRIGGERS
    return StopText(
        situation = if (onCall) R.string.stop_on_call else R.string.stop_call_ended,
        happened = happened(trigger),
        rule = rule(trigger),
        advice = when {
            remote && onCall -> R.string.stop_advice_remote_on_call
            remote -> R.string.stop_advice_remote_after_call
            onCall -> R.string.stop_advice_on_call
            else -> R.string.stop_advice_after_call
        },
        offerHangUp = canHangUp,
        callMyBank = when {
            canHangUp -> R.string.stop_hang_up_and_call_my_bank
            onCall -> null
            else -> R.string.stop_call_my_bank
        },
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
    SensitiveActionKind.ACCESSIBILITY_ENABLED -> R.string.stop_trigger_accessibility
}

/** Who never calls to have this done: a code given, an operation approved, an app installed, an access given. */
@StringRes
private fun rule(trigger: SensitiveActionKind): Int = when (trigger) {
    SensitiveActionKind.ACCESSIBILITY_ENABLED -> R.string.stop_rule_accessibility
    in REMOTE_TRIGGERS -> R.string.stop_rule_remote
    else -> R.string.stop_rule_codes
}

/** Someone may be seeing or controlling the phone: hanging up does not stop that. */
private val REMOTE_TRIGGERS = setOf(
    SensitiveActionKind.REMOTE_CONTROL_OPENED,
    SensitiveActionKind.REMOTE_CONTROL_INSTALLED,
    SensitiveActionKind.ACCESSIBILITY_ENABLED,
)
