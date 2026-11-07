package com.filestech.callhalt.domain.model

/**
 * A fact an adapter observed on the phone, already recognised and time-stamped. The detection engine
 * reads no clock: [at] is the only time it knows, which makes every decision reproducible in a test.
 *
 * Adapters stamp [at] with the PHONE's clocks at reception — never a time the event carries (an SMS's
 * network timestamp): the call window compares it with the call's start, stamped the same way, and a
 * code stamped by the network a few seconds "before" the call would fall outside it. Both clocks, the
 * uptime with the civil time ([Moment]): a civil clock set back during a call must not do the same.
 */
sealed interface Signal {
    val at: Moment
}

data class CallStarted(
    override val at: Moment,
    val channel: CallChannel,
    val direction: CallDirection,
    val counterpart: Counterpart,
) : Signal

data class CallEnded(override val at: Moment, val channel: CallChannel) : Signal

/**
 * An action the scammer needs the victim to perform on this phone, during the call or just after it
 * (PROJET.md, § 3). [source] says where it was seen (a package name, an SMS sender), for the journal.
 */
data class SensitiveAction(
    override val at: Moment,
    val kind: SensitiveActionKind,
    val source: String? = null,
) : Signal

/** A wake-up the engine asked for with [Decision.ScheduleTick], so that time-based rules run on time. */
data class Tick(override val at: Moment) : Signal

/**
 * The phone restarted at [at]. No call survives a restart, and a phone switched off mid-call reports no call state
 * when it boots (measured on Android 16): without this signal, the call would stay "in progress" for hours, and the
 * user's own bank requests would be stopped (docs/ARCHITECTURE.md, D-020). Sent by
 * [com.filestech.callhalt.domain.usecase.ProcessSignalUseCase] only, before the signals that follow it until it is
 * committed — again, harmlessly, if the process dies first: it ends only the calls of an earlier boot, told by their
 * count whatever the civil clock says (P-13). [at] is as late as the restart can be placed — when the process that
 * reports it started, after the restart for sure, whether the phone rebooted or Android alone restarted — and never
 * after the signal it comes before; the engine places the end of the call it cut earlier when the uptime shows the phone
 * itself switched on after that call began.
 */
data class PhoneRestarted(override val at: Moment) : Signal

/**
 * The phone is switching off or restarting, cleanly: the power menu, a battery gone critical, an update (Android's
 * ACTION_SHUTDOWN). Each call in progress ends at [at], and its after-call window runs from there — not from the next
 * start, hours later perhaps, when the user's own bank requests would be stopped (docs/ARCHITECTURE.md, D-028). A call
 * that lives on (Android's user space alone restarted) is reported again by the telephony: a new call. A power cut
 * sends nothing: then [PhoneRestarted] places the end, as before.
 */
data class PhoneShuttingDown(override val at: Moment) : Signal

/**
 * The STOP screen decided for [trigger] could not be shown at all. It re-arms the trigger at once —
 * otherwise the next attempt ("the code did not go through, read it again") would be absorbed as the same
 * burst of a screen nobody saw — and the journal says so: a STOP that did not happen must not read as one
 * that did. Sent by [com.filestech.callhalt.domain.usecase.ProcessSignalUseCase] only.
 */
data class StopNotShown(override val at: Moment, val trigger: SensitiveActionKind) : Signal

/** The user's answer on the STOP screen shown for [trigger]. */
data class StopAnswered(
    override val at: Moment,
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

    /**
     * An app was just given the accessibility access: it can see the screen and act in the user's place — what a
     * remote-control app needs to take the phone in hand, and a spying app to read codes (PROJET.md, § 9.2, idea 1).
     */
    ACCESSIBILITY_ENABLED,
}

enum class StopChoice {
    HANG_UP,
    CALL_MY_BANK,

    /**
     * After an app was given the accessibility access: the phone's accessibility page opens, where the user turns the
     * app off — the app cannot do it itself (docs/ARCHITECTURE.md, D-033). During a call, the call ends first.
     */
    REMOVE_ACCESS,
    CONTINUE,

    /**
     * The screen was left without an answer (Home, another app in front) — not a button. It frees the trigger: a
     * repeat ("press Home and validate again") must show a NEW screen, not be absorbed by one nobody sees. No text
     * to the relative: leaving to hang up from the call screen is an ordinary gesture.
     */
    LEFT,
}
