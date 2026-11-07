package com.filestech.callhalt.domain.usecase

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.PhoneRestarted
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.model.StopNotShown
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.domain.port.DetectorStore
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import kotlin.coroutines.cancellation.CancellationException

/**
 * The single entry point of every adapter (call state, SMS, notifications, packages, the STOP screen):
 * one signal in, its consequences committed, then carried out — preceded, the first time after a restart of the
 * phone, by [PhoneRestarted], and, for a sensitive action, by the call state the phone reports right now
 * ([LiveCallSource]). No adapter calls the engine directly.
 *
 * Adapters run it in a scope that outlives them (the application's), not in a receiver's few seconds: a
 * cancellation between the commit and the screen leaves the STOP recorded but not shown, and only the
 * engine's hold (60 s) limits that.
 */
class ProcessSignalUseCase(
    private val engine: DetectionEngine,
    private val store: DetectorStore,
    private val settings: SettingsSource,
    private val restarts: RestartSource,
    private val liveCall: LiveCallSource,
    private val executor: DecisionExecutor,
) {
    suspend operator fun invoke(signal: Signal) {
        // Asked here, before every signal, so that no adapter can forget it: the calls a restart ended must be over
        // before this signal looks for one (docs/ARCHITECTURE.md, D-020). Recorded only once committed: a process
        // killed in between reports it again, harmlessly. Never after the signal that follows it — a clock corrected
        // forward in between would otherwise end the call this signal starts.
        restarts.unreported()?.let { restart ->
            process(PhoneRestarted(minOf(restart.at, signal.at)))
            restart.record()
        }
        // And the call going on right now, before an action looks for one: a call the broadcasts never told — answered
        // before the first unlock after a start (FN-1) — would leave the whole scam without a STOP (P-7). After the
        // restart, which ends the calls of the earlier boot; at the action's own moment, so that it falls within. A call
        // answered between the action and this read is placed at the action: a STOP for an action a moment before the
        // call, the protective side.
        if (signal is SensitiveAction) tellLiveCall(signal)
        process(signal)
    }

    /**
     * The call state right now, read and committed before [action]. Whatever fails in it — the read, against its port's
     * contract, or the commit of what it read (GPT review of 2026-11-07, 3) — the action must still be handled: lost, it
     * would be the very STOP this read exists for.
     */
    // A catch-all, by design: see above. Swallowed here, where nothing logs (the domain module has no log); the adapter
    // logs its own failures.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun tellLiveCall(action: SensitiveAction) {
        try {
            liveCall.stateAt(action.at)?.let { process(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // The action follows, on what the engine already knew.
        }
    }

    // A catch-all, by design: whatever made the screen fail, the STOP must not stay recorded as shown.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun process(signal: Signal) {
        val current = settings.current()
        val decisions = store.apply { state -> engine.process(state, signal, current) }
        if (decisions.isEmpty()) return
        try {
            executor.execute(decisions)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // The executor throws only when a STOP could not be shown (its contract): each one goes back to
            // the engine, which re-arms its trigger and journals it as not shown — and, for a STOP coming back (FN-4),
            // tells the relative, which is carried out here (review of 2026-11-07, 1: it was dropped). Then the caller
            // learns it.
            val notShown = decisions.filterIsInstance<Decision.ShowStop>().flatMap { stop ->
                store.apply { state -> engine.process(state, StopNotShown(signal.at, stop.trigger), current) }
            }
            carryOut(notShown.filterNot { it is Decision.ShowStop })
            throw failure
        }
    }

    /** What a STOP not shown calls for; a failure there must not hide the one being reported. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun carryOut(decisions: List<Decision>) {
        if (decisions.isEmpty()) return
        try {
            executor.execute(decisions)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // The STOP's own failure is thrown just after: the adapter logs it.
        }
    }
}
