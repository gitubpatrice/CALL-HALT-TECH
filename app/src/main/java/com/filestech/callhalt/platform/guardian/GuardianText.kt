package com.filestech.callhalt.platform.guardian

import android.content.Context
import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * One text to the relative (docs/ARCHITECTURE.md, D-034; the words are Patrice's, 2026-11-07): a [template] that says
 * what was shown or chosen, during a call or just after it, with what happened ([what]) in it. Never the code, nor any
 * word of a message: the relative learns that something happened, not what the user received.
 */
internal data class GuardianText(@StringRes val template: Int, @StringRes val what: Int) {
    fun compose(context: Context): String = context.getString(template, context.getString(what))
}

/**
 * Null for what has no text yet: the long call with an unknown number, whose reminder waits for the number to be read
 * (docs/ARCHITECTURE.md, P-8) — the engine decides it for no call today.
 */
internal fun guardianText(event: GuardianEvent): GuardianText? = when (event) {
    is GuardianEvent.StopShown -> GuardianText(
        if (event.duringCall) R.string.guardian_sms_stop_during_call else R.string.guardian_sms_stop_after_call,
        what(event.trigger),
    )
    is GuardianEvent.StopOverridden -> GuardianText(
        if (event.duringCall) R.string.guardian_sms_continue_during_call else R.string.guardian_sms_continue_after_call,
        what(event.trigger),
    )
    // FN-4 (Patrice, 2026-11-07): "the STOP screen was left without an answer".
    is GuardianEvent.StopLeft -> GuardianText(
        if (event.duringCall) R.string.guardian_sms_left_during_call else R.string.guardian_sms_left_after_call,
        what(event.trigger),
    )
    GuardianEvent.LongCallWithUnknown -> null
}

/** Exhaustive on purpose, as the STOP screen's: a new trigger does not compile until the relative is told what it is. */
@StringRes
private fun what(trigger: SensitiveActionKind): Int = when (trigger) {
    SensitiveActionKind.CODE_MESSAGE -> R.string.guardian_sms_what_code
    SensitiveActionKind.SMS_BURST -> R.string.guardian_sms_what_sms_burst
    SensitiveActionKind.BANK_VALIDATION_REQUEST -> R.string.guardian_sms_what_bank_validation
    SensitiveActionKind.BANK_APP_OPENED -> R.string.guardian_sms_what_bank_app
    SensitiveActionKind.REMOTE_CONTROL_INSTALLED -> R.string.guardian_sms_what_remote_installed
    SensitiveActionKind.REMOTE_CONTROL_OPENED -> R.string.guardian_sms_what_remote_opened
    SensitiveActionKind.ACCESSIBILITY_ENABLED -> R.string.guardian_sms_what_accessibility
}
