package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.PhoneRestarted
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.model.Uptime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * A civil clock set back or forward — by the user, by the network — no longer moves the windows (docs/ARCHITECTURE.md,
 * P-13). Each case is played twice: with the uptime the phone gives, and with the civil clock alone, as the engine
 * decided before. The second run is the negative control: it shows the hole the uptime closes.
 */
class TwoClocksTest {

    private val engine = DetectionEngine()
    private val ten: Instant = Instant.parse("2026-11-04T10:00:00Z")

    /** [wallMinutes] on the civil clock from 10:00; [uptimeMinutes] since boot [boot] — or the civil clock alone. */
    private fun at(wallMinutes: Long, uptimeMinutes: Long, boot: Int = BOOT, withUptime: Boolean = true) = Moment(
        ten.plus(Duration.ofMinutes(wallMinutes)),
        Uptime(boot, Duration.ofMinutes(uptimeMinutes)).takeIf { withUptime },
    )

    private fun callStarted(at: Moment) = CallStarted(at, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden)

    private fun code(at: Moment) = SensitiveAction(at, SensitiveActionKind.CODE_MESSAGE, source = "test")

    private fun run(vararg signals: Signal): Pair<DetectorState, List<Evaluation>> {
        var state = DetectorState()
        val steps = signals.map { signal ->
            engine.process(state, signal, ProtectionSettings()).also { state = it.state }
        }
        return state to steps
    }

    private fun List<Evaluation>.stopsAtLast() = last().decisions.filterIsInstance<Decision.ShowStop>()

    @Test
    fun `a clock set back during a call - the code that follows is still during the call`() {
        fun play(withUptime: Boolean) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 60, withUptime = withUptime)),
            // The civil clock set back half an hour; the code comes three minutes after the start.
            code(at(wallMinutes = -27, uptimeMinutes = 63, withUptime = withUptime)),
        ).second.stopsAtLast()

        assertThat(play(withUptime = true)).hasSize(1)
        assertThat(play(withUptime = false)).isEmpty()
    }

    @Test
    fun `a clock set forward during a call - the call does not outlive the cap for it`() {
        fun play(withUptime: Boolean) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 60, withUptime = withUptime)),
            code(at(wallMinutes = 9 * 60 + 3, uptimeMinutes = 63, withUptime = withUptime)),
        ).second.stopsAtLast()

        assertThat(play(withUptime = true)).hasSize(1)
        assertThat(play(withUptime = false)).isEmpty()
    }

    /** The second call stamped "before" the first one's end: the repeat rule (GPT review, 6) absorbed it. */
    @Test
    fun `a clock set back between two calls - the second is a call of its own`() {
        fun play(withUptime: Boolean) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 10, withUptime = withUptime)),
            CallEnded(at(wallMinutes = 5, uptimeMinutes = 15, withUptime = withUptime), CallChannel.CELLULAR),
            callStarted(at(wallMinutes = -50, uptimeMinutes = 20, withUptime = withUptime)),
            code(at(wallMinutes = -30, uptimeMinutes = 40, withUptime = withUptime)),
        ).second.stopsAtLast()

        assertThat(play(withUptime = true)).hasSize(1)
        assertThat(play(withUptime = false)).isEmpty()
    }

    /** D-020 measured the boot on the civil clock: set back while the phone was off, the cut call stayed open. */
    @Test
    fun `a clock set back while the phone was off - the cut call still ends at the restart`() {
        val restart = at(wallMinutes = -120, uptimeMinutes = 0, boot = BOOT + 1)
        val (state, steps) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            PhoneRestarted(restart),
            code(at(wallMinutes = -110, uptimeMinutes = 10, boot = BOOT + 1)),
            code(at(wallMinutes = -104, uptimeMinutes = 16, boot = BOOT + 1)),
        )

        assertThat(state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(restart)
        // The after-call window runs from the restart, on the new boot's uptime.
        assertThat(steps[2].decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        assertThat(steps.stopsAtLast()).isEmpty()

        val withoutUptime = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100, withUptime = false)),
            PhoneRestarted(at(wallMinutes = -120, uptimeMinutes = 0, withUptime = false)),
        ).first
        assertThat(withoutUptime.calls.getValue(CallChannel.CELLULAR).isActive).isTrue()
    }

    @Test
    fun `a call ended before the restart keeps what was left of its window`() {
        val steps = { codeAfterRestartMinutes: Long ->
            run(
                callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
                CallEnded(at(wallMinutes = 5, uptimeMinutes = 105), CallChannel.CELLULAR),
                // Restarted five minutes after the end: ten minutes of the window are left.
                PhoneRestarted(at(wallMinutes = 10, uptimeMinutes = 0, boot = BOOT + 1)),
                code(at(wallMinutes = 10 + codeAfterRestartMinutes, uptimeMinutes = codeAfterRestartMinutes, boot = BOOT + 1)),
            ).second
        }

        assertThat(steps(9).stopsAtLast()).hasSize(1)
        assertThat(steps(11).stopsAtLast()).isEmpty()
    }

    @Test
    fun `a clock set back while the phone was off counts as no time - the window runs from the restart`() {
        val steps = { codeAfterRestartMinutes: Long ->
            run(
                callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
                CallEnded(at(wallMinutes = 5, uptimeMinutes = 105), CallChannel.CELLULAR),
                PhoneRestarted(at(wallMinutes = -60, uptimeMinutes = 0, boot = BOOT + 1)),
                code(at(wallMinutes = -60 + codeAfterRestartMinutes, uptimeMinutes = codeAfterRestartMinutes, boot = BOOT + 1)),
            ).second
        }

        assertThat(steps(14).stopsAtLast()).hasSize(1)
        assertThat(steps(16).stopsAtLast()).isEmpty()
    }

    /** Closed at the cap, not left open: a later end (a missed call's) would have opened a window on it (M2). */
    @Test
    fun `a call the cap had closed before the restart is not revived`() {
        val (state, steps) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            PhoneRestarted(at(wallMinutes = 9 * 60, uptimeMinutes = 0, boot = BOOT + 1)),
            CallEnded(at(wallMinutes = 9 * 60 + 1, uptimeMinutes = 1, boot = BOOT + 1), CallChannel.CELLULAR),
            code(at(wallMinutes = 9 * 60 + 2, uptimeMinutes = 2, boot = BOOT + 1)),
        )

        assertThat(state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(wallMinutes = 8 * 60, uptimeMinutes = 580))
        assertThat(steps.stopsAtLast()).isEmpty()
    }

    /**
     * Review of 2026-11-04 (M1): the phone restarted at 8:00 in the middle of a call, and the app's process was started
     * only at 10:00, by the user's own bank request. Placed when the process started, the cut call would have ended at
     * 10:00, and that request been stopped. The phone switched on after the call began: the call was cut before that.
     */
    @Test
    fun `the phone itself restarted - the cut call ends when the phone did, not when the app woke`() {
        val callStart = at(wallMinutes = 0, uptimeMinutes = 100)
        // The process started two hours after the phone, which switched on five minutes after the call began.
        val processStart = at(wallMinutes = 125, uptimeMinutes = 120, boot = BOOT + 1)
        val (state, steps) = run(
            callStarted(callStart),
            PhoneRestarted(processStart),
            code(at(wallMinutes = 126, uptimeMinutes = 121, boot = BOOT + 1)),
        )

        assertThat(state.calls.getValue(CallChannel.CELLULAR).endedAt)
            .isEqualTo(at(wallMinutes = 5, uptimeMinutes = 0, boot = BOOT + 1))
        assertThat(steps.stopsAtLast()).isEmpty()
    }

    /**
     * Reported again after a process death, placed later — when the new process started — whatever the civil clock did
     * meanwhile: a call of its own boot is told by the count, not by the time.
     */
    @Test
    fun `a restart reported twice leaves the calls of its own boot running`() {
        val callOfThisBoot = at(wallMinutes = -30, uptimeMinutes = 2, boot = BOOT + 1)
        val (state, _) = run(
            PhoneRestarted(at(wallMinutes = 0, uptimeMinutes = 1, boot = BOOT + 1)),
            callStarted(callOfThisBoot),
            PhoneRestarted(at(wallMinutes = 5, uptimeMinutes = 5, boot = BOOT + 1)),
        )

        val call = state.calls.getValue(CallChannel.CELLULAR)
        assertThat(call.startedAt).isEqualTo(callOfThisBoot)
        assertThat(call.isActive).isTrue()
    }

    /**
     * Android alone restarted (a soft reboot): the count goes up, the uptime keeps running from the phone's own start. The
     * restart is placed when the process that reports it started, minutes ago — not at the uptime's zero, hours ago,
     * which would have closed the window at once.
     */
    @Test
    fun `a restart of Android alone - the window still runs from the restart`() {
        val steps = { codeAfterRestartMinutes: Long ->
            run(
                callStarted(at(wallMinutes = 0, uptimeMinutes = 300)),
                PhoneRestarted(at(wallMinutes = 2, uptimeMinutes = 302, boot = BOOT + 1)),
                code(at(wallMinutes = 2 + codeAfterRestartMinutes, uptimeMinutes = 302 + codeAfterRestartMinutes, boot = BOOT + 1)),
            ).second
        }

        assertThat(steps(14).stopsAtLast()).hasSize(1)
        assertThat(steps(16).stopsAtLast()).isEmpty()
    }

    @Test
    fun `the screens of an earlier boot are gone, whatever the civil clock says`() {
        val (state, _) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            code(at(wallMinutes = 1, uptimeMinutes = 101)),
            PhoneRestarted(at(wallMinutes = -60, uptimeMinutes = 0, boot = BOOT + 1)),
        )

        assertThat(state.pendingStops).isEmpty()
    }

    private companion object {
        const val BOOT = 7
    }
}
