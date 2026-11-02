package com.filestech.callhalt.domain.model

import java.time.Instant

/**
 * A fact an adapter observed on the phone, already recognised and time-stamped. The detection engine
 * reads no clock: [at] is the only time it knows, which makes every decision reproducible in a test.
 *
 * Adapters stamp [at] with the PHONE's clock at reception — never a time the event carries (an SMS's
 * network timestamp): the call window compares it with the call's start, stamped the same way, and a
 * code stamped by the network a few seconds "before" the call would fall outside it.
 */
sealed interface Signal {
    val at: Instant
}

data class CallStarted(
    override val at: Instant,
    val channel: CallChannel,
    val direction: CallDirection,
    val counterpart: Counterpart,
) : Signal

data class CallEnded(override val at: Instant, val channel: CallChannel) : Signal

/**
 * An action the scammer needs the victim to perform on this phone, during the call or just after it
 * (PROJET.md, § 3). [source] says where it was seen (a package name, an SMS sender), for the journal.
 */
data class SensitiveAction(
    override val at: Instant,
    val kind: SensitiveActionKind,
    val source: String? = null,
) : Signal

/** A wake-up the engine asked for with [Decision.ScheduleTick], so that time-based rules run on time. */
data class Tick(override val at: Instant) : Signal

/**
 * The STOP screen decided for [trigger] could not be shown at all. It re-arms the trigger at once —
 * otherwise the next attempt ("the code did not go through, read it again") would be absorbed as the same
 * burst of a screen nobody saw — and the journal says so: a STOP that did not happen must not read as one
 * that did. Sent by [com.filestech.callhalt.domain.usecase.ProcessSignalUseCase] only.
 */
data class StopNotShown(override val at: Instant, val trigger: SensitiveActionKind) : Signal

/** The user's answer on the STOP screen shown for [trigger]. */
data class StopAnswered(
    override val at: Instant,
    val trigger: SensitiveActionKind,
    val choice: StopChoice,
) : Signal

enum class SensitiveActionKind {
    /** An SMS carrying a validation code (PROJET.md, § 4.3). */
    CODE_MESSAGE,

    /** A banking app's notification asking to confirm an operation. */
    BANK_VALIDATION_REQUEST,

    /** A banking app came to the foreground. */
    BANK_APP_OPENED,

    /** A remote-control app (AnyDesk, TeamViewer QuickSupport, RustDesk…) came to the foreground. */
    REMOTE_CONTROL_OPENED,

    /** A remote-control app was installed: warned about even outside a call (PROJET.md, § 4.4). */
    REMOTE_CONTROL_INSTALLED,
}

enum class StopChoice {
    HANG_UP,
    CALL_MY_BANK,
    CONTINUE,

    /**
     * The screen was left without an answer (Home, another app in front) — not a button. It frees the trigger: a
     * repeat ("press Home and validate again") must show a NEW screen, not be absorbed by one nobody sees. No text
     * to the relative: leaving to hang up from the call screen is an ordinary gesture.
     */
    LEFT,
}
