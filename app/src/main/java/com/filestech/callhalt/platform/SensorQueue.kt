package com.filestech.callhalt.platform

import android.content.BroadcastReceiver
import com.filestech.callhalt.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one line every sensor queues in (docs/ARCHITECTURE.md, P-12): what they observed is handled one piece
 * at a time, in the order it ARRIVED. The store's mutex alone serialises the signals but lets them overtake
 * one another: an action stamped just before a new call, handled after it, would lose the previous call's
 * window. A sensor never calls [com.filestech.callhalt.domain.usecase.ProcessSignalUseCase] but from here.
 *
 * Nothing stops the line but the end of the process: a piece of work that fails — a cancellation of its own
 * included — is logged, and the next one runs. Should the line ever stop, every waiting caller is released.
 */
@Singleton
class SensorQueue @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {

    private class Task(val reason: String, private val onDone: () -> Unit, val work: suspend () -> Unit) {
        private val finished = AtomicBoolean(false)

        /** Once, whichever path ends the task: run, failed, or never delivered. */
        fun done() {
            if (finished.compareAndSet(false, true)) onDone()
        }
    }

    private val tasks = Channel<Task>(Channel.UNLIMITED, onUndeliveredElement = Task::done)

    init {
        scope.launch {
            try {
                for (task in tasks) run(task)
            } finally {
                // The scope ended: whatever still waits will never run, and its caller must not wait for it.
                tasks.cancel()
            }
        }
    }

    /** Queues [work]; [onDone] runs once it is over, whether it succeeded or failed, or could not run at all. */
    fun submit(reason: String, onDone: () -> Unit = {}, work: suspend () -> Unit) {
        val task = Task(reason, onDone, work)
        if (tasks.trySend(task).isFailure) {
            Timber.w("Sensor queue stopped, dropped: %s", reason)
            task.done()
        }
    }

    /**
     * Queues [work] for a caller that must be released within [holdLimit] — a broadcast held by `goAsync()`.
     * [release] runs exactly once: when the work is over, or at [holdLimit] if it is still waiting or running,
     * in which case the work goes on without it.
     */
    fun submitHeld(reason: String, holdLimit: Duration, release: () -> Unit, work: suspend () -> Unit) {
        val released = AtomicBoolean(false)
        val releaseOnce = { if (released.compareAndSet(false, true)) release() }
        val deadline = scope.launch {
            delay(holdLimit)
            Timber.w("Broadcast released before its work was over: %s", reason)
            releaseOnce()
        }
        submit(reason, onDone = {
            deadline.cancel()
            releaseOnce()
        }, work = work)
    }

    // A catch-all, by design: whatever one observation throws, the next ones must still be handled.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun run(task: Task) {
        try {
            task.work()
        } catch (cancelled: CancellationException) {
            // The line itself was cancelled: stop. Otherwise the WORK threw it (a timeout, a cancelled call it
            // made): one failure among others — rethrown, it would end the only consumer, silently.
            currentCoroutineContext().ensureActive()
            Timber.w(cancelled, "Sensor work cancelled: %s", task.reason)
        } catch (failure: Exception) {
            Timber.w(failure, "Sensor work failed: %s", task.reason)
        } finally {
            task.done()
        }
    }
}

/**
 * How long a broadcast is held at most. Android allows a background broadcast about ten seconds before it
 * declares the app not responding and may kill it; until Android 13, holding one also delays every other
 * app's background broadcasts. The work is short (one transaction): the limit only matters when it is not.
 */
val BROADCAST_HOLD_LIMIT: Duration = 8.seconds

/**
 * Hands [work] to [queue] while the broadcast is held by `goAsync()`, released once the work is over or at
 * [BROADCAST_HOLD_LIMIT]. `goAsync()` is taken BEFORE the work is queued: the process keeps its priority while
 * it waits its turn. [work] resolves its own `Provider`s, off the main thread (Agenda Tech, audit F2: Hilt
 * injects the fields on the main thread, before `onReceive` runs; the database must not be built there).
 */
fun BroadcastReceiver.queueAsync(queue: SensorQueue, reason: String, work: suspend () -> Unit) {
    val pending = goAsync()
    queue.submitHeld(reason, BROADCAST_HOLD_LIMIT, release = pending::finish, work = work)
}
