package com.filestech.callhalt.platform

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SensorQueueTest {

    /** The point of the queue (P-12): a later observation never overtakes an earlier one still in progress. */
    @Test
    fun `work runs one at a time, in the order it arrived`() = runTest {
        val queue = SensorQueue(backgroundScope)
        val events = mutableListOf<String>()
        val firstMayFinish = CompletableDeferred<Unit>()

        queue.submit("first") {
            events += "first started"
            firstMayFinish.await()
            events += "first done"
        }
        queue.submit("second") { events += "second" }
        drain()
        assertThat(events).containsExactly("first started")

        firstMayFinish.complete(Unit)
        drain()
        assertThat(events).containsExactly("first started", "first done", "second").inOrder()
    }

    @Test
    fun `a failure is not the end of the line, and every caller is released`() = runTest {
        val queue = SensorQueue(backgroundScope)
        val handled = mutableListOf<String>()
        val released = mutableListOf<String>()

        queue.submit("before", onDone = { released += "before" }) { handled += "before" }
        queue.submit("failing", onDone = { released += "failing" }) { error("a broken broadcast") }
        queue.submit("after", onDone = { released += "after" }) { handled += "after" }
        drain()

        assertThat(handled).containsExactly("before", "after").inOrder()
        assertThat(released).containsExactly("before", "failing", "after").inOrder()
    }

    /** Rethrown, a cancellation of the work's own would end the only consumer, and every later signal with it. */
    @Test
    fun `a cancellation thrown by the work does not stop the line`() = runTest {
        val queue = SensorQueue(backgroundScope)
        var after = false
        queue.submit("timed out") { throw CancellationException("a timeout inside the work") }
        queue.submit("after") { after = true }
        drain()
        assertThat(after).isTrue()
    }

    /** The scope ends with the process: whatever still waits will never run, and its caller is released. */
    @Test
    fun `when the line stops, every waiting caller is released, and later ones at once`() = runTest {
        val lineScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val queue = SensorQueue(lineScope)
        val released = mutableListOf<String>()
        queue.submit("blocking", onDone = { released += "blocking" }) { CompletableDeferred<Unit>().await() }
        queue.submit("waiting", onDone = { released += "waiting" }) { error("never runs") }
        drain()

        lineScope.cancel()
        drain()
        queue.submit("late", onDone = { released += "late" }) { error("never runs") }

        assertThat(released).containsExactly("blocking", "waiting", "late")
    }

    @Test
    fun `a held caller is released once, when its work is over`() = runTest {
        val queue = SensorQueue(backgroundScope)
        var releases = 0
        queue.submitHeld("quick", HOLD, release = { releases++ }) { delay(1.seconds) }
        drain()
        advanceTimeBy(2.seconds)
        drain()
        assertThat(releases).isEqualTo(1)

        advanceTimeBy(HOLD * 2)
        drain()
        assertThat(releases).isEqualTo(1)
    }

    /** Held past Android's limit, the broadcast would make the app "not responding": released, the work goes on. */
    @Test
    fun `a held caller is released at the limit, and the work still finishes`() = runTest {
        val queue = SensorQueue(backgroundScope)
        var releases = 0
        var finished = false
        queue.submitHeld("slow", HOLD, release = { releases++ }) {
            delay(HOLD * 2)
            finished = true
        }
        drain()
        advanceTimeBy(HOLD + 1.seconds)
        drain()
        assertThat(releases).isEqualTo(1)
        assertThat(finished).isFalse()

        advanceTimeBy(HOLD * 2)
        drain()
        assertThat(finished).isTrue()
        assertThat(releases).isEqualTo(1)
    }

    /** The witness: without it, the tests above would pass as well if nothing ever ran. */
    @Test
    fun `nothing runs before the queue gets its turn`() = runTest {
        val queue = SensorQueue(backgroundScope)
        var ran = false
        queue.submit("one") { ran = true }
        assertThat(ran).isFalse()
        drain()
        assertThat(ran).isTrue()
    }

    /**
     * Runs what is due now, background work included. `advanceUntilIdle()` would not do: it stops once only
     * background work is left, and the queue's consumer lives in `backgroundScope` (it never ends by itself).
     */
    private fun TestScope.drain() = testScheduler.runCurrent()

    private companion object {
        val HOLD = 8.seconds
    }
}
