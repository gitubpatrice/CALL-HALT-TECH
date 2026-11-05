package com.filestech.callhalt.domain.model

/** What the engine wants done. The app carries it out AFTER the state and the journal are committed. */
sealed interface Decision {
    /** The full-screen interruption. Its words follow [trigger], and whether [call] is still going on or just ended. */
    data class ShowStop(val trigger: SensitiveActionKind, val call: CallSession) : Decision

    /**
     * A notification, not an interruption: a risk worth telling, with no call to interrupt. [source] is what it is about
     * when there is one — the remote-control app installed (a package name), which the warning names.
     */
    data class ShowWarning(val reason: WarningReason, val source: String? = null) : Decision

    /** A text to the trusted relative (PROJET.md, § 4.7), only when the user set one up. */
    data class NotifyGuardian(val event: GuardianEvent) : Decision

    /** Wake the engine with a [Tick] at [at], so that a reminder due during a quiet call fires on time. */
    data class ScheduleTick(val at: Moment) : Decision
}

enum class WarningReason { REMOTE_CONTROL_INSTALLED, LONG_CALL_WITH_UNKNOWN }

sealed interface GuardianEvent {
    data class StopShown(val trigger: SensitiveActionKind) : GuardianEvent

    /** "Continue" chosen despite the STOP screen. */
    data class StopOverridden(val trigger: SensitiveActionKind) : GuardianEvent

    data object LongCallWithUnknown : GuardianEvent
}
