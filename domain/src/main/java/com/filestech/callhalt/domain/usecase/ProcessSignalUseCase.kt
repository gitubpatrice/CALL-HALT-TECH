package com.filestech.callhalt.domain.usecase

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.PhoneRestarted
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.model.StopNotShown
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.domain.port.DetectorStore
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import kotlin.coroutines.cancellation.CancellationException

/**
 * The single entry point of every adapter (call state, SMS, notifications, packages, the STOP screen):
 * one signal in, its consequences committed, then carried out — preceded, the first time after a restart of the
 * phone, by [PhoneRestarted]. No adapter calls the engine directly.
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
        process(signal)
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
            // the engine, which re-arms its trigger and journals it as not shown. Then the caller learns it.
            decisions.filterIsInstance<Decision.ShowStop>().forEach { stop ->
                store.apply { state -> engine.process(state, StopNotShown(signal.at, stop.trigger), current) }
            }
            throw failure
        }
    }
}
