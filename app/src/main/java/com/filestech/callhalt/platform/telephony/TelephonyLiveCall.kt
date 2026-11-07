package com.filestech.callhalt.platform.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.port.LiveCallSource
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's call state read right now (P-7, FN-1), as [liveState] reduces it, then turned into the engine's signal by
 * [callSignal], as the PHONE_STATE broadcast is. Its direction is unknown: only a broadcast tells it.
 *
 * Measured on the Android 16 emulator: answered before the first unlock after a restart, a call reaches the app by no
 * broadcast (the app cannot run then, none of it being direct-boot aware), and none comes once it can; this read finds
 * it, at the app's first start — which the phone causes at the first unlock, binding its notification listener.
 */
@Singleton
class TelephonyLiveCall @Inject constructor(@ApplicationContext private val context: Context) : LiveCallSource {

    override suspend fun stateAt(at: Moment): Signal? =
        current()?.let { state -> callSignal(previous = null, current = state, at = at) }

    /**
     * Null when the state cannot be read: READ_PHONE_STATE withdrawn (from Android 12, the read then throws), the
     * telephony service failing, or a value the platform does not document. Never guessed: a wrong "no call" would end
     * the call the engine knows. One the platform itself gets wrong — Telecom answers "not in a call" when its own
     * service fails — cannot be told from a true one: the call is then ended at the action, and its real end, later, is
     * ignored, the after-call window running from the action (review of 2026-11-07, F2; it takes a dead system server).
     */
    // getCallState: deprecated from Android 12 for getCallStateForSubscription (one SIM at a time) or
    // TelecomManager.isInCall (which does not tell ringing from answered). It still answers, with READ_PHONE_STATE.
    // A catch-all, by design: whatever the read throws, the action it comes before must still be handled.
    @Suppress("DEPRECATION", "TooGenericExceptionCaught")
    private fun current(): PhoneState? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val telecom = context.getSystemService(TelecomManager::class.java)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (telephony == null || telecom == null || !granted) return null
        return try {
            liveState(inCall = telecom.isInCall, inManagedCall = telecom.isInManagedCall, callState = telephony.callState)
        } catch (unreadable: RuntimeException) {
            Timber.w(unreadable, "Call state not readable")
            null
        }
    }
}

/**
 * The call state worth telling the engine, from three reads of the phone. Whether the PHONE_STATE broadcast counts a call
 * placed through an app's own call service (WhatsApp, Signal: "self-managed") is not settled by Android's sources, while
 * [TelephonyManager.getCallState] counts it (review of 2026-11-07, F1). So such a call alone is told neither way: the
 * broadcast alone decides it, as before this read existed — counted here, a call no broadcast ends would stay "in
 * progress", and the action after it, hours later, make a false STOP "your call has just ended".
 * - OFFHOOK only for a call the phone itself manages ([TelecomManager.isInManagedCall]: the SIM's), and with no call
 *   ringing — Telephony's aggregate says ringing first, even over a call in progress;
 * - IDLE only when no call at all goes on ([TelecomManager.isInCall], self-managed ones included);
 * - otherwise null: ringing, or only an app's own call.
 * The three are read one after the other, not at once: they must agree (GPT review of 2026-11-07, 2). A call starting
 * or ending between two reads makes them disagree, and a disagreement tells nothing — read as "no call", it would end a
 * call that goes on.
 */
internal fun liveState(inCall: Boolean, inManagedCall: Boolean, callState: Int): PhoneState? {
    val state = phoneStateOf(callState)
    return when {
        !inCall && !inManagedCall && state == PhoneState.IDLE -> PhoneState.IDLE
        inCall && inManagedCall && state == PhoneState.OFFHOOK -> PhoneState.OFFHOOK
        else -> null
    }
}

/** [TelephonyManager]'s call state as the broadcast names it; null for a value it does not document. */
internal fun phoneStateOf(callState: Int): PhoneState? = when (callState) {
    TelephonyManager.CALL_STATE_IDLE -> PhoneState.IDLE
    TelephonyManager.CALL_STATE_RINGING -> PhoneState.RINGING
    TelephonyManager.CALL_STATE_OFFHOOK -> PhoneState.OFFHOOK
    else -> null
}
