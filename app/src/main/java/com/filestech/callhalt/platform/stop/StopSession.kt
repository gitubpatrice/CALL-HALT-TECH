package com.filestech.callhalt.platform.stop

import android.os.Bundle
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice

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
 *   call buttons, and the advice says to hang up;
 * - [opening]: the answer whose page (the dialler, the settings) waits for the phone to be unlocked — kept with the
 *   screen, so that a screen rebuilt meanwhile asks again: Android drops the first screen's request with it.
 */
data class StopSession(
    val current: StopRequest,
    val triggers: Set<SensitiveActionKind>,
    val answered: Boolean = false,
    val callStillOn: Boolean = false,
    val opening: StopChoice? = null,
) {
    /**
     * A new STOP while this one shows: it takes the screen, and its trigger joins the others. Its call state is the
     * engine's, decided just now — the scammer calling back is a call to offer to cut.
     */
    fun replacedBy(next: StopRequest): StopSession = copy(current = next, triggers = triggers + next.trigger)

    /**
     * A new STOP reaching this screen. Not answered yet, it takes the screen ([replacedBy]). Answered — the phone asking
     * for the unlock before opening the dialler or the settings, which may take a while —, it is a screen of its own:
     * its buttons must answer it, and nothing opens over it; the earlier triggers have their answer.
     */
    fun receiving(next: StopRequest): StopSession = if (answered) of(next) else replacedBy(next)

    /** The phone reported no call on [channel]. */
    fun afterCallEnded(channel: CallChannel): StopSession =
        if (current.onCall && current.channel == channel) copy(current = current.copy(onCall = false)) else this

    /** The phone says no call goes on at all. */
    fun afterNoCallLeft(): StopSession = if (current.onCall) copy(current = current.copy(onCall = false)) else this

    /** [opening]: what the answer opens once the phone is unlocked, if anything. */
    fun answering(opening: StopChoice? = null): StopSession = copy(answered = true, opening = opening)

    /** Its page opened: a second unlock callback opens nothing more. */
    fun opened(): StopSession = copy(opening = null)

    /** "Hang up" did not end the call: no more call buttons on this screen. */
    fun afterCallNotEnded(): StopSession = copy(callStillOn = true)

    fun toBundle(): Bundle = Bundle().apply {
        putBundle(KEY_CURRENT, current.toBundle())
        putStringArray(KEY_TRIGGERS, triggers.map { it.name }.toTypedArray())
        putBoolean(KEY_ANSWERED, answered)
        putBoolean(KEY_CALL_STILL_ON, callStillOn)
        putString(KEY_OPENING, opening?.name)
    }

    companion object {
        private const val KEY_CURRENT = "com.filestech.callhalt.stop.session.CURRENT"
        private const val KEY_TRIGGERS = "com.filestech.callhalt.stop.session.TRIGGERS"
        private const val KEY_ANSWERED = "com.filestech.callhalt.stop.session.ANSWERED"
        private const val KEY_CALL_STILL_ON = "com.filestech.callhalt.stop.session.CALL_STILL_ON"
        private const val KEY_OPENING = "com.filestech.callhalt.stop.session.OPENING"

        fun of(first: StopRequest): StopSession = StopSession(first, setOf(first.trigger))

        /** Null when the bundle holds no whole session: the screen then starts again from its intent. */
        fun fromBundle(bundle: Bundle?): StopSession? {
            val current = StopRequest.fromBundle(bundle?.getBundle(KEY_CURRENT)) ?: return null
            val names = bundle?.getStringArray(KEY_TRIGGERS).orEmpty().toSet()
            val triggers = SensitiveActionKind.entries.filter { it.name in names }.toSet() + current.trigger
            val opening = bundle?.getString(KEY_OPENING)?.let { name -> StopChoice.entries.firstOrNull { it.name == name } }
            return StopSession(
                current,
                triggers,
                answered = bundle?.getBoolean(KEY_ANSWERED) == true,
                callStillOn = bundle?.getBoolean(KEY_CALL_STILL_ON) == true,
                opening = opening,
            )
        }
    }
}
