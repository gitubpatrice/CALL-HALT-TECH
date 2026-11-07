package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.detection.DetectionEngine.Companion.RETURN_ANSWER_WAIT
import com.filestech.callhalt.domain.detection.DetectionEngine.Companion.RETURN_DELAY
import com.filestech.callhalt.domain.detection.DetectionEngine.Companion.RETURN_LATE_LIMIT
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopAnswered
import com.filestech.callhalt.domain.model.StopNotShown

/**
 * The STOP screens left without an answer, that come back once (publication audit, FN-4; Patrice, 2026-11-07;
 * docs/ARCHITECTURE.md, D-038): the engine's rules for them, apart.
 */
internal object StopReturns {

    /**
     * A STOP left without an answer (Home, another app, its notification swiped away). Its trigger is freed at once, as
     * before: a repeat shows a new screen. The first time, during a call or just after it, it is asked back for
     * [RETURN_DELAY] later ([due] decides then). Left again once back, the relative is told: "the STOP screen was left
     * without an answer".
     */
    fun left(state: DetectorState, signal: StopAnswered, settings: ProtectionSettings): Evaluation {
        val trigger = signal.trigger
        val freed = state.copy(pendingStops = state.pendingStops - trigger, stopReturns = state.stopReturns - trigger)
        val journal = listOf(JournalEntry.Answer(signal.at.wall, trigger, signal.choice))
        val back = state.stopReturns[trigger]
        return when {
            back?.returnedAt != null -> Evaluation(freed, journal, told(state, trigger, signal.at, settings))
            !settings.enabled || CallWindow.callAt(state, signal.at, settings.afterCallWindow) == null ->
                Evaluation(freed, journal)
            else -> Evaluation(
                state = freed.copy(stopReturns = freed.stopReturns + (trigger to StopReturn(leftAt = signal.at))),
                journal = journal,
                decisions = listOf(Decision.ScheduleTick(signal.at.plus(RETURN_DELAY))),
            )
        }
    }

    /**
     * A STOP the phone did not show. One coming back that cannot: the user can be told nothing more, the relative is,
     * as for a return left unanswered.
     */
    fun notShown(state: DetectorState, signal: StopNotShown, settings: ProtectionSettings): Evaluation {
        val returned = state.stopReturns[signal.trigger]?.returnedAt != null
        return Evaluation(
            state = state.copy(
                pendingStops = state.pendingStops - signal.trigger,
                stopReturns = state.stopReturns - signal.trigger,
            ),
            journal = listOf(JournalEntry.NotShown(signal.at.wall, signal.trigger)),
            decisions = if (returned) told(state, signal.trigger, signal.at, settings) else emptyList(),
        )
    }

    /**
     * The returns due at [at]. A STOP left [RETURN_DELAY] ago comes back once, by its full-screen notification — unless
     * the call it stood for ended since it was left (the user went to hang up: no alarm for that), or that call's window
     * is over, or another call is now going on. Back and still unanswered [RETURN_ANSWER_WAIT] later, the relative is
     * told. Not due yet (a wake-up that came early): asked again for its own time. Due long ago — more than
     * [RETURN_LATE_LIMIT] late: the process was killed, or the phone slept through the wait — it is dropped, without a
     * STOP nor a text: hours later, both would speak of a call that is no longer the one (review of 2026-11-07, 2).
     */
    fun due(state: DetectorState, at: Moment, settings: ProtectionSettings): Evaluation {
        var next = state
        val decisions = mutableListOf<Decision>()
        state.stopReturns.forEach { (trigger, back) ->
            val returnedAt = back.returnedAt
            val dueAt = returnedAt?.plus(RETURN_ANSWER_WAIT) ?: back.leftAt.plus(RETURN_DELAY)
            when {
                at.isBefore(dueAt) -> decisions += Decision.ScheduleTick(dueAt)
                at.isAfter(dueAt.plus(RETURN_LATE_LIMIT)) -> next = next.copy(
                    pendingStops = if (returnedAt != null) next.pendingStops - trigger else next.pendingStops,
                    stopReturns = next.stopReturns - trigger,
                )
                returnedAt != null -> {
                    next = next.copy(pendingStops = next.pendingStops - trigger, stopReturns = next.stopReturns - trigger)
                    decisions += told(state, trigger, at, settings)
                }
                else -> {
                    val call = CallWindow.callAt(state, at, settings.afterCallWindow)?.takeUnless { it.startedAt.isAfter(back.leftAt) }
                    if (hungUpSince(state, back.leftAt, at) || call == null) {
                        next = next.copy(stopReturns = next.stopReturns - trigger)
                    } else {
                        next = next.copy(
                            pendingStops = next.pendingStops + (trigger to at),
                            stopReturns = next.stopReturns + (trigger to back.copy(returnedAt = at)),
                        )
                        decisions += Decision.ShowStop(trigger, call, returning = true)
                        decisions += Decision.ScheduleTick(at.plus(RETURN_ANSWER_WAIT))
                    }
                }
            }
        }
        return Evaluation(next, decisions = decisions)
    }

    /** The relative told that the STOP was left without an answer, when the user chose one to tell. */
    private fun told(state: DetectorState, trigger: SensitiveActionKind, at: Moment, settings: ProtectionSettings): List<Decision> {
        if (!settings.enabled || !settings.guardianAlerts) return emptyList()
        val duringCall = state.calls.values.any { CallWindow.inProgress(it, at) }
        return listOf(Decision.NotifyGuardian(GuardianEvent.StopLeft(trigger, duringCall)))
    }

    /**
     * A call that was going on when the STOP was left, at [leftAt], and has ended since: the user hung up. Since, not a
     * moment before: a call over before the STOP showed (a code just after it) was not hung up by leaving it (review of
     * 2026-11-07, 4).
     */
    private fun hungUpSince(state: DetectorState, leftAt: Moment, at: Moment): Boolean = state.calls.values.any { call ->
        val end = call.endedAt
        end != null && !call.startedAt.isAfter(leftAt) && !end.isBefore(leftAt) && !end.isAfter(at)
    }
}
