package com.filestech.callhalt.platform.stop

import android.os.Bundle
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * What one STOP screen stands for, from its first request to its answer — pure, so that every rule of the screen
 * is tested on the JVM (general review of 2026-11-02):
 * - [current] is the request on screen; a later STOP replaces it, but its trigger stays in [triggers]: the answer
 *   counts for EVERY trigger the screen absorbed, or the replaced one would stay "shown" and absorb its repeats;
 * - the call: once it is over, the screen no longer says "you are on the phone" nor offers to hang up — and it
 *   never goes back by itself, so a later call (the user calling the real bank) is not offered to be cut; only a
 *   NEW STOP, decided by the engine during that later call, can offer it again;
 * - [answered]: the first answer only; a second tap, or leaving after an answer, counts for nothing;
 * - [callStillOn]: "Hang up" was pressed and the call could not be ended — the screen stays, without its
 *   call buttons, and the advice says to hang up.
 */
data class StopSession(
    val current: StopRequest,
    val triggers: Set<SensitiveActionKind>,
    val answered: Boolean = false,
    val callStillOn: Boolean = false,
) {
    /**
     * A new STOP while this one shows: it takes the screen, and its trigger joins the others. Its call state is the
     * engine's, decided just now — the scammer calling back is a call to offer to cut.
     */
    fun replacedBy(next: StopRequest): StopSession = copy(current = next, triggers = triggers + next.trigger)

    /** The phone reported no call on [channel]. */
    fun afterCallEnded(channel: CallChannel): StopSession =
        if (current.onCall && current.channel == channel) copy(current = current.copy(onCall = false)) else this

    /** The phone says no call goes on at all. */
    fun afterNoCallLeft(): StopSession = if (current.onCall) copy(current = current.copy(onCall = false)) else this

    fun answering(): StopSession = copy(answered = true)

    /** "Hang up" did not end the call: no more call buttons on this screen. */
    fun afterCallNotEnded(): StopSession = copy(callStillOn = true)

    fun toBundle(): Bundle = Bundle().apply {
        putBundle(KEY_CURRENT, current.toBundle())
        putStringArray(KEY_TRIGGERS, triggers.map { it.name }.toTypedArray())
        putBoolean(KEY_ANSWERED, answered)
        putBoolean(KEY_CALL_STILL_ON, callStillOn)
    }

    companion object {
        private const val KEY_CURRENT = "com.filestech.callhalt.stop.session.CURRENT"
        private const val KEY_TRIGGERS = "com.filestech.callhalt.stop.session.TRIGGERS"
        private const val KEY_ANSWERED = "com.filestech.callhalt.stop.session.ANSWERED"
        private const val KEY_CALL_STILL_ON = "com.filestech.callhalt.stop.session.CALL_STILL_ON"

        fun of(first: StopRequest): StopSession = StopSession(first, setOf(first.trigger))

        /** Null when the bundle holds no whole session: the screen then starts again from its intent. */
        fun fromBundle(bundle: Bundle?): StopSession? {
            val current = StopRequest.fromBundle(bundle?.getBundle(KEY_CURRENT)) ?: return null
            val names = bundle?.getStringArray(KEY_TRIGGERS).orEmpty().toSet()
            val triggers = SensitiveActionKind.entries.filter { it.name in names }.toSet() + current.trigger
            return StopSession(current, triggers, bundle?.getBoolean(KEY_ANSWERED) == true, bundle?.getBoolean(KEY_CALL_STILL_ON) == true)
        }
    }
}
