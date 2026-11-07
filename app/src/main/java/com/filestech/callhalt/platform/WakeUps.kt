package com.filestech.callhalt.platform

import com.filestech.callhalt.di.ApplicationScope
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.Tick
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Wakes the engine at a moment it asked for ([com.filestech.callhalt.domain.model.Decision.ScheduleTick]). */
fun interface WakeUps {
    fun at(moment: Moment)
}

/**
 * In this process, which the notification listener keeps running (D-023): a coroutine's wait, never the civil clock,
 * which a change of time would shift (docs/ARCHITECTURE.md, P-8, D-026). Then a [Tick], stamped when it fires, through
 * the sensor queue like every signal. Asked for: the return of a STOP left without an answer, 15 s later, and the wait
 * for its answer, 2 min (FN-4). Two limits, written in D-038 (review of 2026-11-07, 2 and 3): the wait stops while the
 * phone sleeps deeply (the monotonic clock) — during a call the phone keeps awake; just after it, in a pocket, it may
 * come late, and the engine drops a return more than 15 min late; and a process killed loses it — the next start asks
 * for one at once (CallHaltApplication), which looks at the returns left in the database.
 */
@Singleton
class ProcessWakeUps @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val queue: SensorQueue,
    private val clock: PhoneClock,
    /** Resolved in the queue, never at injection time: it reaches the database. */
    private val processSignal: Provider<ProcessSignalUseCase>,
) : WakeUps {

    override fun at(moment: Moment) {
        val wait = clock.now().until(moment).coerceAtLeast(Duration.ZERO)
        scope.launch {
            delay(wait.toMillis())
            queue.submit(reason = "wake-up") { processSignal.get()(Tick(clock.now())) }
        }
    }
}
