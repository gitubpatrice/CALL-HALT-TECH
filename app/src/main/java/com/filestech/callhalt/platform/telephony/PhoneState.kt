package com.filestech.callhalt.platform.telephony

import android.telephony.TelephonyManager
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.Signal

/**
 * The phone's call state, as the PHONE_STATE broadcast names it ([TelephonyManager.EXTRA_STATE]). Each name IS the
 * value the platform sends: `EXTRA_STATE_IDLE` and its siblings are computed at run time, not compile-time constants,
 * so they cannot be named here without breaking every JVM test. `PhoneStateOnDeviceTest` holds the two together.
 */
enum class PhoneState {
    IDLE,
    RINGING,
    OFFHOOK,
    ;

    companion object {
        /** Null for a value the platform did not send: a broadcast that says nothing is ignored, not guessed. */
        fun fromExtra(value: String?): PhoneState? = entries.firstOrNull { it.name == value }
    }
}

/**
 * What the phone's call state, reported at [at], means for the engine. [previous] is the state reported before
 * it, or null when unknown.
 *
 * OFFHOOK always starts a call and IDLE always ends one, whatever came before. The engine absorbs a repeat (the
 * broadcast comes twice) and an end with no call in progress; and a start or an end this sensor missed — the
 * process killed, the phone restarted mid-call — heals at the next one, instead of being taken on trust from a
 * memory that may be wrong. [previous] only tells the direction.
 *
 * RINGING is not a call: nobody has answered yet (a code arriving during the ringing is P-6). During a call it is
 * a second one waiting, which changes nothing: the scam began with the first.
 *
 * The number needs READ_CALL_LOG, not requested at this step: the counterpart is [Counterpart.Unreadable], which
 * the long-call reminder does not count as unknown (docs/ARCHITECTURE.md, § 6).
 */
fun callSignal(previous: PhoneState?, current: PhoneState, at: Moment): Signal? = when (current) {
    PhoneState.RINGING -> null
    PhoneState.OFFHOOK -> CallStarted(at, CallChannel.CELLULAR, direction(previous), Counterpart.Unreadable)
    PhoneState.IDLE -> CallEnded(at, CallChannel.CELLULAR)
}

/** Answered after ringing, dialled from idle; after OFFHOOK (a repeat) or nothing known, the sensor cannot tell. */
private fun direction(previous: PhoneState?): CallDirection = when (previous) {
    PhoneState.RINGING -> CallDirection.INCOMING
    PhoneState.IDLE -> CallDirection.OUTGOING
    PhoneState.OFFHOOK, null -> CallDirection.UNKNOWN
}
