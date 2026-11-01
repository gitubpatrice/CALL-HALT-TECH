package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.model.StopAnswered
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.StopNotShown
import com.filestech.callhalt.domain.model.Tick
import com.filestech.callhalt.domain.model.WarningReason
import java.time.Duration

/** The outcome of one signal: the state to commit, what to journal, and what to do once committed. */
data class Evaluation(
    val state: DetectorState,
    val journal: List<JournalEntry> = emptyList(),
    val decisions: List<Decision> = emptyList(),
)

/**
 * The method of Call Halt (PROJET.md, § 3) as a pure function: never trust the number; stop the user
 * when a sensitive action happens during a call or just after it.
 *
 * No clock, no I/O, no Android: (state, signal, settings) → [Evaluation]. With protection off, calls are
 * still tracked but nothing is decided. Turned back on in the middle of a call, the STOP rule knows the
 * call; the long-call reminder does not until a [Tick] — whatever turns protection back on, or lowers the
 * threshold, sends one (docs/ARCHITECTURE.md, P-8).
 */
class DetectionEngine {

    fun process(state: DetectorState, signal: Signal, settings: ProtectionSettings): Evaluation =
        when (signal) {
            is CallStarted -> onCallStarted(state, signal, settings)
            is CallEnded -> onCallEnded(state, signal)
            is SensitiveAction -> onSensitiveAction(state, signal, settings)
            is Tick -> onTick(state, signal, settings)
            is StopAnswered -> onStopAnswered(state, signal, settings)
            is StopNotShown -> Evaluation(
                state = state.copy(pendingStops = state.pendingStops - signal.trigger),
                journal = listOf(JournalEntry.NotShown(signal.at, signal.trigger)),
            )
        }

    private fun onCallStarted(state: DetectorState, signal: CallStarted, settings: ProtectionSettings): Evaluation {
        val current = state.calls[signal.channel]
        // A second start on a channel already in a call is the same conversation (call waiting, a
        // conference): the scam began with the first one, whose start time the window must keep.
        val sameConversation = current != null && CallWindow.inProgress(current, signal.at)
        // A start the ENDED call already covers is that call's, repeated or delivered late (the phone-state
        // broadcast comes twice): taken as new, it would revive the call for hours (GPT review, 6).
        val repeatOfEnded = current?.endedAt?.let { signal.at.isBefore(it) } == true
        if (sameConversation || repeatOfEnded) return Evaluation(state)

        val call = CallSession(signal.channel, signal.direction, signal.counterpart, startedAt = signal.at)
        val next = state.copy(
            calls = state.calls + (signal.channel to call),
            longCallWarned = state.longCallWarned - signal.channel,
        )
        val decisions = if (settings.enabled && call.isWithUnknown) {
            listOf(Decision.ScheduleTick(call.startedAt.plus(settings.longCallThreshold)))
        } else {
            emptyList()
        }
        return Evaluation(next, decisions = decisions)
    }

    private fun onCallEnded(state: DetectorState, signal: CallEnded): Evaluation {
        val current = state.calls[signal.channel]
        // An end with no call in progress (a repeated broadcast, a call that began before the install)
        // changes nothing. Nor does an end from well before this call started: it is an EARLIER call's,
        // repeated or delivered late, and must not end this one (GPT review, 3).
        val belongsToThisCall = current != null && current.isActive &&
            !signal.at.isBefore(current.startedAt.minus(SIGNAL_CLOCK_TOLERANCE))
        if (current == null || !belongsToThisCall) return Evaluation(state)
        // Within the tolerance, an end stamped before the start is clamped to it.
        val ended = current.copy(endedAt = maxOf(signal.at, current.startedAt))
        return Evaluation(state.copy(calls = state.calls + (signal.channel to ended)))
    }

    private fun onSensitiveAction(state: DetectorState, signal: SensitiveAction, settings: ProtectionSettings): Evaluation {
        if (!settings.enabled) return Evaluation(state)
        val call = CallWindow.callAt(state, signal.at, settings.afterCallWindow)
        return when {
            call == null -> outsideCall(state, signal)
            isSameBurst(state, signal, call) -> Evaluation(state)
            else -> stopDuring(call, state, signal, settings)
        }
    }

    /**
     * A repeat of a trigger whose screen — shown during THIS call — is unanswered and recent. Bounded on both
     * sides: a screen stamped in the "future" (the clock was set back) must not absorb repeats for as long as
     * the clock lags (GPT review, 7).
     */
    private fun isSameBurst(state: DetectorState, signal: SensitiveAction, call: CallSession): Boolean {
        val shownAt = state.pendingStops[signal.kind] ?: return false
        return !shownAt.isBefore(call.startedAt) &&
            !signal.at.isBefore(shownAt) &&
            signal.at.isBefore(shownAt.plus(UNANSWERED_STOP_HOLD))
    }

    private fun stopDuring(call: CallSession, state: DetectorState, signal: SensitiveAction, settings: ProtectionSettings): Evaluation {
        val decisions = buildList {
            add(Decision.ShowStop(signal.kind, call))
            if (settings.guardianAlerts) add(Decision.NotifyGuardian(GuardianEvent.StopShown(signal.kind)))
        }
        return Evaluation(
            state = state.copy(pendingStops = state.pendingStops + (signal.kind to signal.at)),
            journal = listOf(JournalEntry.Stop(signal.at, signal.kind, signal.source, call.counterpart)),
            decisions = decisions,
        )
    }

    private fun outsideCall(state: DetectorState, signal: SensitiveAction): Evaluation =
        when (signal.kind) {
            SensitiveActionKind.REMOTE_CONTROL_INSTALLED -> Evaluation(
                state = state,
                journal = listOf(JournalEntry.Warning(signal.at, WarningReason.REMOTE_CONTROL_INSTALLED)),
                decisions = listOf(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED)),
            )
            // Outside a call these are ordinary: the user pays, checks the account, gets a code.
            SensitiveActionKind.CODE_MESSAGE,
            SensitiveActionKind.BANK_VALIDATION_REQUEST,
            SensitiveActionKind.BANK_APP_OPENED,
            SensitiveActionKind.REMOTE_CONTROL_OPENED,
            -> Evaluation(state)
        }

    private fun onTick(state: DetectorState, signal: Tick, settings: ProtectionSettings): Evaluation {
        if (!settings.enabled) return Evaluation(state)
        val watched = state.calls.values.filter {
            CallWindow.inProgress(it, signal.at) && it.isWithUnknown && it.channel !in state.longCallWarned
        }
        val (reached, notYet) = watched.partition {
            !signal.at.isBefore(it.startedAt.plus(settings.longCallThreshold))
        }

        val decisions = buildList {
            if (reached.isNotEmpty()) {
                add(Decision.ShowWarning(WarningReason.LONG_CALL_WITH_UNKNOWN))
                if (settings.guardianAlerts) add(Decision.NotifyGuardian(GuardianEvent.LongCallWithUnknown))
            }
            // A tick that came early (or a threshold raised since) asks again for the next due time.
            notYet.minOfOrNull { it.startedAt.plus(settings.longCallThreshold) }
                ?.let { add(Decision.ScheduleTick(it)) }
        }
        return Evaluation(
            state = state.copy(longCallWarned = state.longCallWarned + reached.map { it.channel }),
            journal = if (reached.isEmpty()) emptyList() else listOf(JournalEntry.Warning(signal.at, WarningReason.LONG_CALL_WITH_UNKNOWN)),
            decisions = decisions,
        )
    }

    private fun onStopAnswered(state: DetectorState, signal: StopAnswered, settings: ProtectionSettings): Evaluation {
        // The answer is a fact and is journaled even if protection was turned off meanwhile.
        val overridden = signal.choice == StopChoice.CONTINUE
        val decisions = if (settings.enabled && settings.guardianAlerts && overridden) {
            listOf(Decision.NotifyGuardian(GuardianEvent.StopOverridden(signal.trigger)))
        } else {
            emptyList()
        }
        return Evaluation(
            state = state.copy(pendingStops = state.pendingStops - signal.trigger),
            journal = listOf(JournalEntry.Answer(signal.at, signal.trigger, signal.choice)),
            decisions = decisions,
        )
    }

    companion object {
        /**
         * How long an UNANSWERED STOP screen absorbs repeats of its trigger. Past it, the screen was
         * probably killed without an answer, and the next signal shows a new one.
         */
        val UNANSWERED_STOP_HOLD: Duration = Duration.ofSeconds(60)

        /**
         * How far before a call's start its end may be stamped and still be ITS end: two signals stamped by the
         * phone's clock a moment apart. Further back, the end belongs to an earlier call.
         */
        val SIGNAL_CLOCK_TOLERANCE: Duration = Duration.ofSeconds(5)
    }
}
