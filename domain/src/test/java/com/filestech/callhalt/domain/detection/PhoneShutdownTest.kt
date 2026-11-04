package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.PhoneRestarted
import com.filestech.callhalt.domain.model.PhoneShuttingDown
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
 * A phone switched off cleanly says so, and the call in progress ends there (docs/ARCHITECTURE.md, D-028): its window
 * runs from the shutdown, not from the next start. Without it — a power cut — the end is placed when the phone switched
 * back on (D-020), which is what the negative controls show.
 */
class PhoneShutdownTest {

    private val engine = DetectionEngine()
    private val ten: Instant = Instant.parse("2026-11-04T10:00:00Z")

    /** [wallMinutes] on the civil clock from 10:00; [uptimeMinutes] since boot [boot]. */
    private fun at(wallMinutes: Long, uptimeMinutes: Long, boot: Int = BOOT) =
        Moment(ten.plus(Duration.ofMinutes(wallMinutes)), Uptime(boot, Duration.ofMinutes(uptimeMinutes)))

    private fun callStarted(at: Moment) = CallStarted(at, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden)

    private fun code(at: Moment) = SensitiveAction(at, SensitiveActionKind.CODE_MESSAGE, source = "test")

    private fun run(vararg signals: Signal?): Pair<DetectorState, List<Evaluation>> {
        var state = DetectorState()
        val steps = signals.filterNotNull().map { signal ->
            engine.process(state, signal, ProtectionSettings()).also { state = it.state }
        }
        return state to steps
    }

    private fun List<Evaluation>.stopsAtLast() = last().decisions.filterIsInstance<Decision.ShowStop>()

    @Test
    fun `a clean shutdown ends the call in progress where it happens`() {
        val shutdown = at(wallMinutes = 5, uptimeMinutes = 105)
        val (state, steps) = run(callStarted(at(wallMinutes = 0, uptimeMinutes = 100)), PhoneShuttingDown(shutdown))

        assertThat(state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(shutdown)
        assertThat(steps.last().journal).isEmpty()
        assertThat(steps.last().decisions).isEmpty()
    }

    /**
     * The case D-020 observed: a phone switched off in the middle of a call and back on hours later, the user's own
     * code right after — a STOP "the call has just ended" for a call over for hours.
     */
    @Test
    fun `switched off for hours - the user's own code after the start is not stopped`() {
        fun play(heard: Boolean) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            PhoneShuttingDown(at(wallMinutes = 5, uptimeMinutes = 105)).takeIf { heard },
            // Switched back on three hours later; the app started a minute after the phone.
            PhoneRestarted(at(wallMinutes = 186, uptimeMinutes = 1, boot = BOOT + 1)),
            code(at(wallMinutes = 187, uptimeMinutes = 2, boot = BOOT + 1)),
        ).second.stopsAtLast()

        assertThat(play(heard = true)).isEmpty()
        assertThat(play(heard = false)).hasSize(1)
    }

    /** "Switch your phone off and on again": what was left of the window still protects after the start. */
    @Test
    fun `restarted at once - what was left of the window still runs`() {
        val steps = { codeAtWallMinutes: Long ->
            run(
                callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
                PhoneShuttingDown(at(wallMinutes = 5, uptimeMinutes = 105)),
                // Off for two minutes: the window, from the shutdown, runs until 10:20.
                PhoneRestarted(at(wallMinutes = 7, uptimeMinutes = 0, boot = BOOT + 1)),
                code(at(wallMinutes = codeAtWallMinutes, uptimeMinutes = codeAtWallMinutes - 7, boot = BOOT + 1)),
            ).second
        }

        assertThat(steps(19).stopsAtLast()).hasSize(1)
        assertThat(steps(21).stopsAtLast()).isEmpty()
    }

    @Test
    fun `a call already ended keeps its end`() {
        val end = at(wallMinutes = 3, uptimeMinutes = 103)
        val (state, _) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            CallEnded(end, CallChannel.CELLULAR),
            PhoneShuttingDown(at(wallMinutes = 5, uptimeMinutes = 105)),
        )

        assertThat(state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(end)
    }

    /** Past the 8-hour cap the call is a missed end, not a call: closed at the cap, with no window (review M2). */
    @Test
    fun `a shutdown past the cap closes the call at the cap`() {
        val (state, _) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            PhoneShuttingDown(at(wallMinutes = 9 * 60, uptimeMinutes = 100 + 9 * 60)),
        )

        assertThat(state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(wallMinutes = 8 * 60, uptimeMinutes = 580))
    }

    /**
     * Off longer than the window: as after a hang-up followed by a wait of the same length, which the window does not
     * cover either (review of 2026-11-04, M1). A scammer still has to call back, and that call is protected.
     */
    @Test
    fun `switched off longer than the window - the window is over at the start`() {
        val (_, steps) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            PhoneShuttingDown(at(wallMinutes = 5, uptimeMinutes = 105)),
            PhoneRestarted(at(wallMinutes = 25, uptimeMinutes = 1, boot = BOOT + 1)),
            code(at(wallMinutes = 26, uptimeMinutes = 2, boot = BOOT + 1)),
            callStarted(at(wallMinutes = 30, uptimeMinutes = 6, boot = BOOT + 1)),
            code(at(wallMinutes = 31, uptimeMinutes = 7, boot = BOOT + 1)),
        )

        assertThat(steps[3].decisions.filterIsInstance<Decision.ShowStop>()).isEmpty()
        assertThat(steps.stopsAtLast()).hasSize(1)
    }

    /**
     * A restart of Android's user space goes through the same shutdown (AOSP `PowerManagerService` → `ShutdownThread`),
     * and the call may live on: the telephony then reports it again (measured after a soft reboot, D-026). It is a new
     * call, and the window of the one just ended covers the seconds in between.
     */
    @Test
    fun `a call that lives on after the shutdown is followed as a new one`() {
        val again = at(wallMinutes = 6, uptimeMinutes = 106)
        val (state, steps) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            PhoneShuttingDown(at(wallMinutes = 5, uptimeMinutes = 105)),
            code(at(wallMinutes = 5, uptimeMinutes = 105)),
            callStarted(again),
            code(at(wallMinutes = 40, uptimeMinutes = 140)),
        )

        assertThat(steps[2].decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        val call = state.calls.getValue(CallChannel.CELLULAR)
        assertThat(call.startedAt).isEqualTo(again)
        assertThat(call.isActive).isTrue()
        assertThat(steps.stopsAtLast()).hasSize(1)
    }

    @Test
    fun `both channels end, and a second shutdown changes nothing`() {
        val shutdown = at(wallMinutes = 5, uptimeMinutes = 105)
        val (state, steps) = run(
            callStarted(at(wallMinutes = 0, uptimeMinutes = 100)),
            CallStarted(at(wallMinutes = 1, uptimeMinutes = 101), CallChannel.VOIP, CallDirection.OUTGOING, Counterpart.Hidden),
            PhoneShuttingDown(shutdown),
            PhoneShuttingDown(at(wallMinutes = 6, uptimeMinutes = 106)),
        )

        assertThat(state.calls.values.map { it.endedAt }).containsExactly(shutdown, shutdown)
        assertThat(steps.last().state).isEqualTo(steps[2].state)
    }

    @Test
    fun `a shutdown with no call changes nothing`() {
        val (state, steps) = run(PhoneShuttingDown(at(wallMinutes = 5, uptimeMinutes = 105)))

        assertThat(state).isEqualTo(DetectorState())
        assertThat(steps.single().journal).isEmpty()
    }

    private companion object {
        const val BOOT = 7
    }
}
