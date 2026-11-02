package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Turns a change of the phone's call state into the engine's call signals (step 1 of REPRISE.md). */
@Singleton
class CallStateSensor @Inject constructor(
    private val memory: PhoneStateMemory,
    private val processSignal: ProcessSignalUseCase,
    private val events: CallEvents,
) {
    /** Called from the [com.filestech.callhalt.platform.SensorQueue] only, one state at a time, in arrival order. */
    suspend fun onPhoneState(current: PhoneState, at: Instant) {
        val previous = memory.swap(current)
        val signal = callSignal(previous, current, at)
        // Debug builds only (no tree in release). The signal's TYPE, never the signal: once the number is read,
        // its toString() would print it (security review of 2026-11-02).
        Timber.d("Phone state %s after %s: %s", current, previous, signal?.javaClass?.simpleName)
        if (signal == null) return
        // Told after the engine: a screen that reads the end must find it committed.
        try {
            processSignal(signal)
        } finally {
            if (signal is CallEnded) events.callEnded(signal.channel)
        }
    }
}
