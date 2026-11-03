package com.filestech.callhalt.platform.stop

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Only the screen can say it reached the front: Android blocks a background start without telling the caller. */
class StopDisplayTest {

    @Test
    fun `a screen that reports itself in front in time is shown`() = runTest {
        val display = StopDisplay()
        val id = display.nextId()
        val waiting = async { display.awaitShown(id, 5.seconds) }
        runCurrent()
        display.shown(id)
        assertThat(waiting.await()).isTrue()
    }

    @Test
    fun `a screen that never reports itself is not shown`() = runTest {
        val display = StopDisplay()
        assertThat(display.awaitShown(display.nextId(), 5.seconds)).isFalse()
    }

    /** The screen can report itself before the executor starts waiting: no wake-up is missed. */
    @Test
    fun `a screen that got through before the wait began is shown`() = runTest {
        val display = StopDisplay()
        val id = display.nextId()
        display.shown(id)
        assertThat(display.awaitShown(id, 5.seconds)).isTrue()
    }

    /** A later STOP took the screen first (single task): the earlier one got through too. */
    @Test
    fun `a later screen in front counts for an earlier request`() = runTest {
        val display = StopDisplay()
        val first = display.nextId()
        display.shown(display.nextId())
        assertThat(display.awaitShown(first, 5.seconds)).isTrue()
    }

    /** Asked without waiting, once the notification is posted (D-024): this screen, or a later one, got through. */
    @Test
    fun `shown already is this request or a later one, never an earlier one`() {
        val display = StopDisplay()
        val first = display.nextId()
        val second = display.nextId()
        assertThat(display.isShown(first)).isFalse()
        display.shown(first)
        assertThat(display.isShown(first)).isTrue()
        assertThat(display.isShown(second)).isFalse()
        display.shown(second)
        assertThat(display.isShown(first)).isTrue()
    }

    /** The witness: an EARLIER screen in front does not stand for a later one that never came. */
    @Test
    fun `an earlier screen in front does not count for a later request`() = runTest {
        val display = StopDisplay()
        val first = display.nextId()
        display.shown(first)
        assertThat(display.awaitShown(display.nextId(), 5.seconds)).isFalse()
    }
}
