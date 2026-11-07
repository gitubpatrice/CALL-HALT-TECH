package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.PhoneRestarted
import com.filestech.callhalt.domain.model.PhoneShuttingDown
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
            is PhoneRestarted -> onPhoneRestarted(state, signal)
            // Each call in progress ends there, by the rule of an end heard — the cap included (D-028).
            is PhoneShuttingDown -> Evaluation(
                state.calls.keys.fold(state) { current, channel -> onCallEnded(current, CallEnded(signal.at, channel)).state },
            )
            is StopAnswered -> if (signal.choice == StopChoice.LEFT) {
                StopReturns.left(state, signal, settings)
            } else {
                onStopAnswered(state, signal, settings)
            }
            is StopNotShown -> StopReturns.notShown(state, signal, settings)
        }

    private fun onCallStarted(state: DetectorState, signal: CallStarted, settings: ProtectionSettings): Evaluation {
        val current = state.calls[signal.channel]
        // A second start on a channel already in a call is the same conversation (call waiting, a
        // conference): the scam began with the first one, whose start time the window must keep.
        val sameConversation = current != null && CallWindow.inProgress(current, signal.at)
        // A start the ENDED call already covers is that call's, repeated or delivered late (the phone-state
        // broadcast comes twice): taken as new, it would revive the call for hours (GPT review, 6). Stamped at
        // reception on the uptime, in arrival order, only a sensor breaking Signal's contract could send one; a
        // civil clock set back between two calls no longer makes the second look like a repeat (P-13).
        val repeatOfEnded = current?.endedAt?.let { signal.at.isBefore(it) } == true
        if (sameConversation || repeatOfEnded) {
            // The phone's call state, read when the process started, told this call first, its direction unknown
            // (D-035); the broadcast that started the process tells it a moment later. Taken, not lost: the start stays.
            val directionTold = current?.takeIf {
                sameConversation && it.direction == CallDirection.UNKNOWN && signal.direction != CallDirection.UNKNOWN
            }?.copy(direction = signal.direction)
            return Evaluation(directionTold?.let { state.copy(calls = state.calls + (signal.channel to it)) } ?: state)
        }

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
        // Within the tolerance, an end stamped before the start is clamped to it. Past the cap, the call is a missed
        // end, not a call (CallWindow): an end that comes later — a missed call's, hours after — closes it at the cap,
        // and opens no after-call window (review of 2026-11-04, M2).
        val cap = current.startedAt.plus(CallWindow.MAX_TRACKED_CALL)
        val ended = current.copy(endedAt = if (signal.at.isAfter(cap)) cap else maxOf(signal.at, current.startedAt))
        return Evaluation(state.copy(calls = state.calls + (signal.channel to ended)))
    }

    /**
     * No call survives a restart, nor any screen (D-020). Every call of an earlier boot — by the boot's count, whatever
     * the civil clock says — is carried into this one, to be measured from here on on this boot's uptime (P-13). One
     * still in progress ends when the restart cut it, at the latest — the after-call window runs from there, the
     * protective side of an end nobody saw — unless the 8-hour cap had closed it: it is closed at the cap, not revived.
     * Still in progress: the phone was cut off, or the app did not hear the shutdown that ends it ([PhoneShuttingDown]).
     * An ended one keeps what was left of its window. That gap is measured once, here, on the civil clock, the only one
     * two boots share; set back while the phone was off, it counts as no time at all, the protective side again. A call
     * of THIS boot — the restart reported again after a process death, later — is left as it is. The screens shown
     * before the restart are gone: their triggers are re-armed. Tracked whatever the settings, like every call.
     */
    private fun onPhoneRestarted(state: DetectorState, signal: PhoneRestarted): Evaluation {
        val restart = signal.at
        val calls = state.calls.mapValues { (_, call) ->
            if (call.startedAt.isOfEarlierBootThan(restart)) call.carriedOver(restart) else call
        }
        val pendingStops = state.pendingStops.filterValues { shownAt -> !shownAt.isOfEarlierBootThan(restart) }
        // A screen left before the restart is gone with it: it does not come back (FN-4).
        val stopReturns = state.stopReturns.filterValues { back -> !back.leftAt.isOfEarlierBootThan(restart) }
        return Evaluation(state.copy(calls = calls, pendingStops = pendingStops, stopReturns = stopReturns))
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
            if (settings.guardianAlerts) {
                add(Decision.NotifyGuardian(GuardianEvent.StopShown(signal.kind, CallWindow.inProgress(call, signal.at))))
            }
        }
        // A new screen for this trigger: the one left no longer comes back (FN-4).
        return Evaluation(
            state = state.copy(
                pendingStops = state.pendingStops + (signal.kind to signal.at),
                stopReturns = state.stopReturns - signal.kind,
            ),
            journal = listOf(JournalEntry.Stop(signal.at.wall, signal.kind, signal.source, call.counterpart)),
            decisions = decisions,
        )
    }

    private fun outsideCall(state: DetectorState, signal: SensitiveAction): Evaluation =
        when (signal.kind) {
            SensitiveActionKind.REMOTE_CONTROL_INSTALLED -> Evaluation(
                state = state,
                journal = listOf(JournalEntry.Warning(signal.at.wall, WarningReason.REMOTE_CONTROL_INSTALLED, signal.source)),
                decisions = listOf(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, signal.source)),
            )
            // Outside a call these are ordinary: the user pays, checks the account, gets a code.
            SensitiveActionKind.CODE_MESSAGE,
            SensitiveActionKind.BANK_VALIDATION_REQUEST,
            SensitiveActionKind.BANK_APP_OPENED,
            SensitiveActionKind.REMOTE_CONTROL_OPENED,
            // The user's own choice outside a call: a screen reader, a switch access, an app he trusts.
            SensitiveActionKind.ACCESSIBILITY_ENABLED,
            -> Evaluation(state)
        }

    /** The long-call reminder, and the STOP screens left that are due back ([StopReturns.due], FN-4). */
    private fun onTick(state: DetectorState, signal: Tick, settings: ProtectionSettings): Evaluation {
        // Turned off meanwhile: nothing comes back.
        if (!settings.enabled) return Evaluation(state.copy(stopReturns = emptyMap()))
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
        val reminded = state.copy(longCallWarned = state.longCallWarned + reached.map { it.channel })
        val journal = if (reached.isEmpty()) {
            emptyList()
        } else {
            listOf(JournalEntry.Warning(signal.at.wall, WarningReason.LONG_CALL_WITH_UNKNOWN))
        }
        val returns = StopReturns.due(reminded, signal.at, settings)
        return Evaluation(returns.state, journal + returns.journal, decisions + returns.decisions)
    }

    private fun onStopAnswered(state: DetectorState, signal: StopAnswered, settings: ProtectionSettings): Evaluation {
        // The answer is a fact and is journaled even if protection was turned off meanwhile.
        // "Continue" chosen within the window of a call only, as the STOP rule: a screen left on a sleeping phone and
        // answered the next morning is no "just after a call, call right away" (review of 2026-11-07, faux positifs 1).
        // The relative was told when the STOP showed.
        val overridden = signal.choice == StopChoice.CONTINUE &&
            CallWindow.callAt(state, signal.at, settings.afterCallWindow) != null
        val decisions = if (settings.enabled && settings.guardianAlerts && overridden) {
            val duringCall = state.calls.values.any { CallWindow.inProgress(it, signal.at) }
            listOf(Decision.NotifyGuardian(GuardianEvent.StopOverridden(signal.trigger, duringCall)))
        } else {
            emptyList()
        }
        // An answer, at last: nothing comes back (FN-4).
        return Evaluation(
            state = state.copy(pendingStops = state.pendingStops - signal.trigger, stopReturns = state.stopReturns - signal.trigger),
            journal = listOf(JournalEntry.Answer(signal.at.wall, signal.trigger, signal.choice)),
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
         * How long after a STOP was left it comes back (FN-4): long enough to hang up from the call screen — then it
         * does not — short enough to be back before the code is read out.
         */
        val RETURN_DELAY: Duration = Duration.ofSeconds(RETURN_DELAY_SECONDS)

        /** How long a STOP that came back may wait for an answer before the relative is told (FN-4). */
        val RETURN_ANSWER_WAIT: Duration = Duration.ofMinutes(RETURN_ANSWER_WAIT_MINUTES)

        private const val RETURN_DELAY_SECONDS = 15L
        private const val RETURN_ANSWER_WAIT_MINUTES = 2L

        /**
         * How far before a call's start its end may be stamped and still be ITS end: two signals stamped by the
         * phone's clock a moment apart. Further back, the end belongs to an earlier call.
         */
        val SIGNAL_CLOCK_TOLERANCE: Duration = Duration.ofSeconds(5)
    }
}

/** A call of an earlier boot, carried into the one [restart] reported (see `onPhoneRestarted`). */
private fun CallSession.carriedOver(restart: Moment): CallSession {
    val end = endedAt
    return when {
        end == null -> {
            val cut = cutBy(restart)
            val cap = startedAt.plus(CallWindow.MAX_TRACKED_CALL)
            copy(endedAt = if (startedAt.until(cut) > CallWindow.MAX_TRACKED_CALL) cap else cut)
        }
        end.isBefore(restart) -> copy(endedAt = restart.minus(end.until(restart).coerceAtLeast(Duration.ZERO)))
        else -> this
    }
}

/**
 * When the restart cut this call, at the latest. [restart] is when the app's process started, which can be hours after
 * the phone did. If the phone itself switched on after the call began — its start, the uptime's zero, is after the
 * call's on the civil clock — the call was cut before that, when it went off: the end is placed there (review of
 * 2026-11-04, M1). Otherwise Android alone restarted, its uptime running on from before the call, or the civil clock
 * was set back: only the restart bounds it.
 */
private fun CallSession.cutBy(restart: Moment): Moment {
    val phoneStart = restart.uptime?.let { restart.minus(it.sinceBoot) } ?: return restart
    return if (phoneStart.wall.isBefore(startedAt.wall)) restart else phoneStart
}
