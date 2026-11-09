package com.filestech.callhalt.domain.model

/** What the engine wants done. The app carries it out AFTER the state and the journal are committed. */
sealed interface Decision {
    /**
     * The full-screen interruption. Its words follow [trigger], and whether [call] is still going on or just ended.
     * [returning]: a STOP left without an answer, coming back once (FN-4) — by its full-screen notification, not over
     * what the user went to. [source]: what the action came from, when known (a package name) — the STOP of a
     * remote-control app installed offers to uninstall it (FN-3, D-039); a STOP coming back does not know it.
     */
    data class ShowStop(
        val trigger: SensitiveActionKind,
        val call: CallSession,
        val returning: Boolean = false,
        val source: String? = null,
    ) : Decision

    /**
     * A notification, not an interruption: a risk worth telling, with no call to interrupt. [source] is what it is about
     * when there is one — the remote-control app installed (a package name), which the warning names. [withStop]: a STOP
     * says it first, during the call or just after it; the notification waits, silent, and stays once the call is over
     * (FN-3, D-039).
     */
    data class ShowWarning(val reason: WarningReason, val source: String? = null, val withStop: Boolean = false) : Decision

    /** A text to the trusted relative (PROJET.md, § 4.7), only when the user set one up. */
    data class NotifyGuardian(val event: GuardianEvent) : Decision

    /** Wake the engine with a [Tick] at [at], so that a reminder due during a quiet call fires on time. */
    data class ScheduleTick(val at: Moment) : Decision
}

enum class WarningReason { REMOTE_CONTROL_INSTALLED, LONG_CALL_WITH_UNKNOWN }

/**
 * What the relative is told (docs/ARCHITECTURE.md, D-034). [duringCall]: a call was still going on — the text says
 * "during a call", else "just after a call".
 */
sealed interface GuardianEvent {
    data class StopShown(val trigger: SensitiveActionKind, val duringCall: Boolean) : GuardianEvent

    /** "Continue" chosen despite the STOP screen. */
    data class StopOverridden(val trigger: SensitiveActionKind, val duringCall: Boolean) : GuardianEvent

    /** The STOP screen left without an answer, and again once it came back (or it could not come back) (FN-4). */
    data class StopLeft(val trigger: SensitiveActionKind, val duringCall: Boolean) : GuardianEvent

    data object LongCallWithUnknown : GuardianEvent
}
