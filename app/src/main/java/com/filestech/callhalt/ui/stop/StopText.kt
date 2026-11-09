package com.filestech.callhalt.ui.stop

import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.platform.stop.UNINSTALL_TRIGGERS

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
    /**
     * The safe ways out, the most protective first; "Continue" is none of them and comes after. Empty while a call
     * goes on that the app cannot end: dialling, or opening the settings, would leave the scammer on the line instead
     * of cutting him off (D-011) — the advice says to hang up.
     */
    val buttons: List<StopButton>,
)

/** One safe way out: its words — with [app], the name they hold, if any — and the answer it gives. */
internal data class StopButton(@StringRes val label: Int, val choice: StopChoice, val app: String? = null)

/**
 * [onCall] and [channel] are those of the decision's call ([com.filestech.callhalt.domain.model.Decision.ShowStop]);
 * [canEndCall] says whether the app holds the permission to end a call. [installedApp]: the name of the remote-control
 * app just installed, when the screen can offer to uninstall it (FN-3, D-039).
 */
internal fun stopText(
    trigger: SensitiveActionKind,
    onCall: Boolean,
    channel: CallChannel,
    canEndCall: Boolean,
    installedApp: String? = null,
): StopText {
    val canHangUp = onCall && channel == CallChannel.CELLULAR && canEndCall
    val uninstall = installedApp?.takeIf { trigger in UNINSTALL_TRIGGERS }
    return StopText(
        situation = if (onCall) R.string.stop_on_call else R.string.stop_call_ended,
        happened = happened(trigger),
        rule = rule(trigger),
        advice = advice(trigger, onCall),
        buttons = buttons(trigger, onCall, canHangUp, uninstall),
    )
}

/** Exhaustive on purpose: a new trigger does not compile until it says what happened. */
@StringRes
private fun happened(trigger: SensitiveActionKind): Int = when (trigger) {
    SensitiveActionKind.CODE_MESSAGE -> R.string.stop_trigger_code
    SensitiveActionKind.SMS_BURST -> R.string.stop_trigger_sms_burst
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

/**
 * Hanging up does not end a remote-control session, nor what an app given the accessibility access does: the advice
 * for those triggers is to switch the phone off. The access itself outlives the phone's restart: it is turned off
 * first, in the phone's settings (D-033).
 */
@StringRes
private fun advice(trigger: SensitiveActionKind, onCall: Boolean): Int = when {
    trigger == SensitiveActionKind.ACCESSIBILITY_ENABLED ->
        if (onCall) R.string.stop_advice_accessibility_on_call else R.string.stop_advice_accessibility_after_call
    trigger in REMOTE_TRIGGERS -> if (onCall) R.string.stop_advice_remote_on_call else R.string.stop_advice_remote_after_call
    onCall -> R.string.stop_advice_on_call
    else -> R.string.stop_advice_after_call
}

/**
 * The call ends first when the app can end it, and nothing else is offered while it goes on (D-011). After an app was
 * given the accessibility access, removing it comes before calling the bank: in a few touches, it stops what the app
 * can do in the user's place — the bank's operations included. After a remote-control app was installed, uninstalling
 * it ([uninstall], its name) comes first likewise: no one can then use it (Patrice, 2026-11-07, FN-3).
 */
private fun buttons(trigger: SensitiveActionKind, onCall: Boolean, canHangUp: Boolean, uninstall: String?): List<StopButton> {
    val access = trigger == SensitiveActionKind.ACCESSIBILITY_ENABLED
    return when {
        canHangUp -> listOf(
            when {
                access -> StopButton(R.string.stop_hang_up_and_remove_access, StopChoice.REMOVE_ACCESS)
                uninstall != null -> StopButton(R.string.stop_hang_up_and_uninstall, StopChoice.UNINSTALL, uninstall)
                else -> StopButton(R.string.stop_hang_up, StopChoice.HANG_UP)
            },
            StopButton(R.string.stop_hang_up_and_call_my_bank, StopChoice.CALL_MY_BANK),
        )
        onCall -> emptyList()
        else -> listOfNotNull(
            StopButton(R.string.stop_remove_access, StopChoice.REMOVE_ACCESS).takeIf { access },
            uninstall?.let { StopButton(R.string.stop_uninstall, StopChoice.UNINSTALL, it) },
            StopButton(R.string.stop_call_my_bank, StopChoice.CALL_MY_BANK),
        )
    }
}

/** Someone may be seeing or controlling the phone: hanging up does not stop that. */
private val REMOTE_TRIGGERS = setOf(
    SensitiveActionKind.REMOTE_CONTROL_OPENED,
    SensitiveActionKind.REMOTE_CONTROL_INSTALLED,
    SensitiveActionKind.ACCESSIBILITY_ENABLED,
)
