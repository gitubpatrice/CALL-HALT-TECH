package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * At every start of the process, the call state the phone reports right now (P-7, FN-1). The first start after the
 * phone's comes at the first unlock, and may find a call already answered that no broadcast told: told now, its end —
 * which a broadcast does tell — opens the after-call window. Told only at the next sensitive action
 * ([ProcessSignalUseCase]), a call that ends before any would leave none.
 *
 * A start that a PHONE_STATE broadcast caused queues this first: the broadcast's own start, a moment later, is then the
 * same conversation, and the call's direction stays unknown — which no rule reads.
 */
@Singleton
class CallStateAtStart @Inject constructor(
    private val queue: SensorQueue,
    private val clock: PhoneClock,
    private val liveCall: LiveCallSource,
    /** Resolved in the queue, never on the main thread: it reaches the database. */
    private val processSignal: Provider<ProcessSignalUseCase>,
) {
    fun check() {
        val at = clock.now()
        queue.submit("call state at start") { liveCall.stateAt(at)?.let { processSignal.get()(it) } }
    }
}
