package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.platform.notification.SmsBurst.Companion.THRESHOLD
import com.filestech.callhalt.platform.notification.SmsBurst.Companion.WINDOW_MS
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** A flood of SMS from different numbers, counted in the SMS app's conversations (publication audit, N-3, D-040). */
class SmsBurstTest {

    private val burst = SmsBurst()

    private fun receiveFrom(count: Int, at: Long = 0, firstKey: Int = 0): List<Boolean> =
        (firstKey until firstKey + count).map { burst.received("conversation $it", at) }

    @Test
    fun `different conversations make a burst at the threshold, not before`() {
        assertThat(receiveFrom(THRESHOLD - 1)).doesNotContain(true)
        assertThat(burst.received("one more", 0)).isTrue()
    }

    /** The same number writing again, or a notification delivered twice: one conversation. */
    @Test
    fun `the same conversation counts once, however many messages it brings`() {
        repeat(THRESHOLD * 3) { assertThat(burst.received("the bank", it.toLong())).isFalse() }
        assertThat(receiveFrom(THRESHOLD - 2)).doesNotContain(true)
    }

    @Test
    fun `conversations older than the window no longer count`() {
        receiveFrom(THRESHOLD - 1, at = 0)
        assertThat(burst.received("late", WINDOW_MS + 1)).isFalse()
        // Those of the window's very edge still do.
        receiveFrom(THRESHOLD - 1, at = 1_000, firstKey = 100)
        assertThat(burst.received("in time", WINDOW_MS + 1_000)).isTrue()
    }

    /** Told once: another burst needs as many different conversations again. */
    @Test
    fun `once told, the count starts over`() {
        receiveFrom(THRESHOLD)
        assertThat(receiveFrom(THRESHOLD - 1, firstKey = 100)).doesNotContain(true)
        assertThat(burst.received("again", 0)).isTrue()
    }

    /** The monotonic clock does not go back but across a restart, which is a new process: a time ahead is dropped. */
    @Test
    fun `a time after the present is not kept`() {
        receiveFrom(THRESHOLD - 1, at = 5_000)
        assertThat(burst.received("now", 1_000)).isFalse()
    }
}
