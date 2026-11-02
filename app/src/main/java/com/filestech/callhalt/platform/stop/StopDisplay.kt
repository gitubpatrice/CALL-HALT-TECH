package com.filestech.callhalt.platform.stop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Whether a STOP screen really got through. Android blocks an activity started from the background WITHOUT
 * telling the caller (no exception, a line in the system log): only the screen itself can say it exists. A STOP
 * that did not get through must never be recorded as shown (the executor's contract).
 *
 * "Got through" is the screen being CREATED, not resumed: a start Android blocks never creates it, while one made
 * with the phone asleep — against the cheek, during a call — is created at once and shown on waking, over the
 * lock screen (general review of 2026-11-02, M1).
 */
@Singleton
class StopDisplay @Inject constructor() {

    /**
     * Seeded with the monotonic clock: ids grow across process restarts within a boot, so a screen rebuilt after
     * the process died never takes the id — and the saved countdown — of another.
     */
    private val lastIssued = AtomicLong(System.nanoTime())
    private val lastShown = MutableStateFlow(Long.MIN_VALUE)

    /** The id of the next screen asked for. */
    fun nextId(): Long = lastIssued.incrementAndGet()

    /** Called by [StopActivity] with request [id], as soon as it holds it. */
    fun shown(id: Long) = lastShown.update { maxOf(it, id) }

    /** True once request [id] — or a later one, shown in its place — got through within [within]. */
    suspend fun awaitShown(id: Long, within: Duration): Boolean =
        withTimeoutOrNull(within) { lastShown.first { it >= id } } != null
}
