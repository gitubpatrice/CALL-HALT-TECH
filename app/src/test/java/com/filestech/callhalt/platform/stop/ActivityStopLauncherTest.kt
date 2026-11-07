package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * The launcher's rules, on the JVM (coherence audit of 2026-11-07, C2): the screen, waited for; the notification when the
 * phone blocks the screen, or at once without the display over the others; the screen alone deciding when no
 * notification can be posted; the screen that gets through between the wait and the notification (R-02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityStopLauncherTest {

    private val display = StopDisplay()
    private val call = CallSession(
        CallChannel.CELLULAR,
        CallDirection.INCOMING,
        Counterpart.Unreadable,
        startedAt = Moment(Instant.parse("2026-11-07T10:00:00Z")),
    )
    private val stop = Decision.ShowStop(SensitiveActionKind.CODE_MESSAGE, call)

    /** The screen Android is asked for; [created] when it says the screen got through. */
    private inner class FakeStarter(
        private val overlay: Boolean,
        private val accepts: Boolean = true,
        private val created: Boolean = false,
    ) : ScreenStarter {
        val asked = mutableListOf<StopRequest>()

        override fun mayStartFromBackground() = overlay

        override fun start(request: StopRequest): Boolean {
            asked += request
            if (created) display.shown(request.id)
            return accepts
        }
    }

    /** The notification; [screenArrivesMeanwhile]: the screen gets through just as it is posted (R-02). */
    private inner class FakeNotice(
        private val allowed: Boolean = true,
        private val waiting: List<SensitiveActionKind> = emptyList(),
        private val screenArrivesMeanwhile: Boolean = false,
    ) : StopNotice {
        val posted = mutableListOf<StopRequest>()
        val cancelled = mutableListOf<Long>()

        override fun pending() = waiting

        override fun post(request: StopRequest): Boolean {
            posted += request
            if (screenArrivesMeanwhile) display.shown(request.id)
            return allowed
        }

        override fun cancel(shownId: Long) {
            cancelled += shownId
        }
    }

    @Test
    fun `with the display over the others, a screen that gets through is shown, and nothing is posted`() = runTest {
        val notice = FakeNotice()

        assertThat(ActivityStopLauncher(FakeStarter(overlay = true, created = true), display, notice).show(stop)).isTrue()

        assertThat(notice.posted).isEmpty()
    }

    /** FN-4: a STOP left without an answer comes back by its notification alone, never over what the user went to. */
    @Test
    fun `a returning STOP is posted as its notification, and no screen is started`() = runTest {
        val starter = FakeStarter(overlay = true, created = true)
        val notice = FakeNotice()

        assertThat(ActivityStopLauncher(starter, display, notice).show(stop.copy(returning = true))).isTrue()

        assertThat(starter.asked).isEmpty()
        assertThat(notice.posted).hasSize(1)
        assertThat(currentTime).isEqualTo(0)
    }

    /** Its notification refused: not shown — the engine then tells the relative (FN-4). */
    @Test
    fun `a returning STOP whose notification is refused is not shown`() = runTest {
        val starter = FakeStarter(overlay = true, created = true)

        assertThat(ActivityStopLauncher(starter, display, FakeNotice(allowed = false)).show(stop.copy(returning = true))).isFalse()

        assertThat(starter.asked).isEmpty()
    }

    /** Samsung, Xiaomi (D-024): the phone blocks the screen in silence. */
    @Test
    fun `a screen the phone blocks is waited for 5 seconds, then the notification`() = runTest {
        val notice = FakeNotice()

        assertThat(ActivityStopLauncher(FakeStarter(overlay = true), display, notice).show(stop)).isTrue()

        assertThat(currentTime).isEqualTo(ActivityStopLauncher.SHOW_TIMEOUT.inWholeMilliseconds)
        assertThat(notice.posted).hasSize(1)
        // No screen got through: the notification stays (review of 2026-11-07, T2).
        assertThat(notice.cancelled).isEmpty()
    }

    @Test
    fun `a start refused at once goes to the notification, without waiting`() = runTest {
        val notice = FakeNotice()

        assertThat(ActivityStopLauncher(FakeStarter(overlay = true, accepts = false), display, notice).show(stop)).isTrue()

        assertThat(currentTime).isEqualTo(0)
        assertThat(notice.posted).hasSize(1)
    }

    /** D-030: without it, Android blocks a screen started from the background — the notification at once, 5 s sooner. */
    @Test
    fun `without the display over the others, the notification at once`() = runTest {
        val notice = FakeNotice()

        assertThat(ActivityStopLauncher(FakeStarter(overlay = false), display, notice).show(stop)).isTrue()

        assertThat(currentTime).isEqualTo(0)
        assertThat(notice.posted).hasSize(1)
    }

    /** Review of 2026-11-04: the app in front lets the screen through; else a STOP shown would be journaled as not shown. */
    @Test
    fun `with no notification either, the screen alone decides, waited for`() = runTest {
        val starter = FakeStarter(overlay = false)
        val launcher = ActivityStopLauncher(starter, display, FakeNotice(allowed = false))
        backgroundScope.launch {
            delay(2.seconds)
            display.shown(starter.asked.single().id)
        }

        assertThat(launcher.show(stop)).isTrue()

        assertThat(currentTime).isEqualTo(2.seconds.inWholeMilliseconds)
    }

    /** Review of 2026-11-07, T1: a screen refused at once is not waited for, even with no notification to fall back on. */
    @Test
    fun `a screen refused at once is not waited for, notification or not`() = runTest {
        val launcher = ActivityStopLauncher(FakeStarter(overlay = false, accepts = false), display, FakeNotice(allowed = false))

        assertThat(launcher.show(stop)).isFalse()

        assertThat(currentTime).isEqualTo(0)
    }

    /** Both tried, both failed (GPT review of 2026-11-07, 2: "false" alone would let a launcher that tries nothing pass). */
    @Test
    fun `with neither the screen nor a notification, the STOP is not shown`() = runTest {
        listOf(false, true).forEach { overlay ->
            val starter = FakeStarter(overlay = overlay)
            val notice = FakeNotice(allowed = false)

            assertThat(ActivityStopLauncher(starter, display, notice).show(stop)).isFalse()

            assertThat(starter.asked).hasSize(1)
            assertThat(notice.posted).hasSize(1)
        }
    }

    /** R-02: the notification would stay, and a touch later open a STOP already answered. */
    @Test
    fun `a screen that gets through as the notification is posted takes it away`() = runTest {
        val notice = FakeNotice(screenArrivesMeanwhile = true)
        val starter = FakeStarter(overlay = true)

        assertThat(ActivityStopLauncher(starter, display, notice).show(stop)).isTrue()

        assertThat(notice.cancelled).containsExactly(starter.asked.single().id)
    }

    /** N-1: the screen started now stands for the triggers of a notification still waiting. */
    @Test
    fun `a notification still waiting is carried into the request`() = runTest {
        val starter = FakeStarter(overlay = true, created = true)
        val notice = FakeNotice(waiting = listOf(SensitiveActionKind.ACCESSIBILITY_ENABLED))

        ActivityStopLauncher(starter, display, notice).show(stop)

        assertThat(starter.asked.single().earlier).containsExactly(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(starter.asked.single().trigger).isEqualTo(SensitiveActionKind.CODE_MESSAGE)
    }

    private val TestScope.currentTime: Long get() = testScheduler.currentTime
}
