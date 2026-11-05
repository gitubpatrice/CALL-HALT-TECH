package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.PhoneRestarted
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.model.StopAnswered
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.StopNotShown
import com.filestech.callhalt.domain.model.Tick
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.Instant

class DetectionEngineTest {

    private val engine = DetectionEngine()
    private val t0: Instant = Instant.parse("2026-11-01T10:00:00Z")
    private val unknownNumber = Counterpart.Number("+33612345678", inContacts = false)
    private val contact = Counterpart.Number("+33698765432", inContacts = true)

    private fun at(minutes: Long, seconds: Long = 0) = Moment(t0.plus(Duration.ofMinutes(minutes).plusSeconds(seconds)))

    private fun callStarted(
        minutes: Long = 0,
        counterpart: Counterpart = unknownNumber,
        channel: CallChannel = CallChannel.CELLULAR,
    ) = CallStarted(at(minutes), channel, CallDirection.INCOMING, counterpart)

    private fun action(kind: SensitiveActionKind, minutes: Long, seconds: Long = 0) =
        SensitiveAction(at(minutes, seconds), kind, source = "test")

    /** Runs the signals in order from an empty state, like the store would, and returns every step. */
    private fun run(vararg signals: Signal, settings: ProtectionSettings = ProtectionSettings()): List<Evaluation> {
        var state = DetectorState()
        return signals.map { signal ->
            engine.process(state, signal, settings).also { state = it.state }
        }
    }

    private fun List<Evaluation>.lastStops() = last().decisions.filterIsInstance<Decision.ShowStop>()

    @Nested
    inner class TheStopRule {

        @ParameterizedTest
        @EnumSource(SensitiveActionKind::class)
        fun `every sensitive action during a call shows STOP and is journaled`(kind: SensitiveActionKind) {
            val steps = run(callStarted(), action(kind, minutes = 3))

            val stop = steps.lastStops().single()
            assertThat(stop.trigger).isEqualTo(kind)
            assertThat(stop.call.startedAt).isEqualTo(at(0))
            assertThat(steps.last().journal).containsExactly(JournalEntry.Stop(at(3).wall, kind, "test", unknownNumber))
        }

        @Test
        fun `a saved contact does not silence it - a spoofed bank number is a contact too`() {
            val steps = run(callStarted(counterpart = contact), action(SensitiveActionKind.CODE_MESSAGE, minutes = 1))

            assertThat(steps.lastStops()).hasSize(1)
        }

        @Test
        fun `it holds up to the end of the after-call window, bound included`() {
            val ended = CallEnded(at(5), CallChannel.CELLULAR)

            val inside = run(callStarted(), ended, action(SensitiveActionKind.CODE_MESSAGE, minutes = 20))
            val outside = run(callStarted(), ended, action(SensitiveActionKind.CODE_MESSAGE, minutes = 20, seconds = 1))

            assertThat(inside.lastStops()).hasSize(1)
            assertThat(outside.last().decisions).isEmpty()
        }

        @Test
        fun `a window of zero still covers the call itself`() {
            val settings = ProtectionSettings(afterCallWindow = Duration.ZERO)

            val during = run(callStarted(), action(SensitiveActionKind.CODE_MESSAGE, minutes = 2), settings = settings)
            val after = run(
                callStarted(),
                CallEnded(at(2), CallChannel.CELLULAR),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 2, seconds = 1),
                settings = settings,
            )

            assertThat(during.lastStops()).hasSize(1)
            assertThat(after.last().decisions).isEmpty()
        }

        @Test
        fun `an action before the call is not during it`() {
            val steps = run(callStarted(minutes = 5), action(SensitiveActionKind.CODE_MESSAGE, minutes = 4))

            assertThat(steps.last().decisions).isEmpty()
        }

        @Test
        fun `with no call at all, nothing happens`() {
            val steps = run(action(SensitiveActionKind.BANK_VALIDATION_REQUEST, minutes = 0))

            assertThat(steps.last().decisions).isEmpty()
            assertThat(steps.last().journal).isEmpty()
        }

        @Test
        fun `a VOIP call counts as a call`() {
            val steps = run(
                callStarted(counterpart = Counterpart.Unreadable, channel = CallChannel.VOIP),
                action(SensitiveActionKind.BANK_APP_OPENED, minutes = 1),
            )

            assertThat(steps.lastStops()).hasSize(1)
        }
    }

    @Nested
    inner class RepeatsAndAnswers {

        @Test
        fun `a burst of the same action shows one screen while it is unanswered`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1, seconds = 20),
            )

            assertThat(steps.last().decisions).isEmpty()
            assertThat(steps.last().journal).isEmpty()
        }

        @Test
        fun `an answer re-arms the trigger at once - validate again is stopped again`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.BANK_VALIDATION_REQUEST, minutes = 1),
                StopAnswered(at(1, 10), SensitiveActionKind.BANK_VALIDATION_REQUEST, StopChoice.CONTINUE),
                action(SensitiveActionKind.BANK_VALIDATION_REQUEST, minutes = 1, seconds = 20),
            )

            assertThat(steps.lastStops()).hasSize(1)
        }

        @Test
        fun `an unanswered screen stops absorbing repeats after its hold`() {
            val hold = DetectionEngine.UNANSWERED_STOP_HOLD.seconds
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1, seconds = hold),
            )

            assertThat(steps.lastStops()).hasSize(1)
        }

        @Test
        fun `a screen stamped in the future (clock set back) does not absorb the next actions`() {
            // A call since 9:00, a STOP at 10:00; the clock is then set back to 9:31, the call still going.
            // Without the lower bound, 9:31 sits "before 10:01" and would be absorbed for half an hour.
            val steps = run(
                callStarted(minutes = -60),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 0),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = -29),
            )

            assertThat(steps.lastStops()).hasSize(1)
        }

        @Test
        fun `another action is a new danger, even while a screen is up`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                action(SensitiveActionKind.REMOTE_CONTROL_OPENED, minutes = 1, seconds = 5),
            )

            assertThat(steps.lastStops().single().trigger).isEqualTo(SensitiveActionKind.REMOTE_CONTROL_OPENED)
        }

        @Test
        fun `an unanswered screen of a previous call does not absorb the next call's first action`() {
            // The second call replaces the first in the state (same channel). The 1:00 screen, still
            // inside its hold at 1:30, was shown before this call began: it must not absorb its action.
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                CallEnded(at(1, 5), CallChannel.CELLULAR),
                CallStarted(at(1, 20), CallChannel.CELLULAR, CallDirection.INCOMING, unknownNumber),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1, seconds = 30),
            )

            assertThat(steps.lastStops().single().call.startedAt).isEqualTo(at(1, 20))
        }

        @Test
        fun `a STOP that could not be shown re-arms its trigger and is journaled as not shown`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                StopNotShown(at(1), SensitiveActionKind.CODE_MESSAGE),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1, seconds = 20),
            )

            assertThat(steps[2].journal).containsExactly(JournalEntry.NotShown(at(1).wall, SensitiveActionKind.CODE_MESSAGE))
            assertThat(steps[2].decisions).isEmpty()
            assertThat(steps.lastStops()).hasSize(1)
        }

        @Test
        fun `every answer is journaled`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                StopAnswered(at(1, 30), SensitiveActionKind.CODE_MESSAGE, StopChoice.HANG_UP),
            )

            assertThat(steps.last().journal)
                .containsExactly(JournalEntry.Answer(at(1, 30).wall, SensitiveActionKind.CODE_MESSAGE, StopChoice.HANG_UP))
            assertThat(steps.last().state.pendingStops).isEmpty()
        }

        /** "Press Home and validate again": the repeat must show a NEW screen, not be absorbed by one nobody sees. */
        @Test
        fun `a screen left without an answer frees its trigger, and the repeat is stopped again`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.BANK_VALIDATION_REQUEST, minutes = 1),
                StopAnswered(at(1, 5), SensitiveActionKind.BANK_VALIDATION_REQUEST, StopChoice.LEFT),
                action(SensitiveActionKind.BANK_VALIDATION_REQUEST, minutes = 1, seconds = 25),
            )

            assertThat(steps[2].journal)
                .containsExactly(JournalEntry.Answer(at(1, 5).wall, SensitiveActionKind.BANK_VALIDATION_REQUEST, StopChoice.LEFT))
            assertThat(steps.last().decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        }
    }

    @Nested
    inner class TheRelative {

        private val withGuardian = ProtectionSettings(guardianAlerts = true)

        @Test
        fun `is told when STOP shows, only when set up`() {
            val told = run(callStarted(), action(SensitiveActionKind.CODE_MESSAGE, minutes = 1), settings = withGuardian)
            val notSetUp = run(callStarted(), action(SensitiveActionKind.CODE_MESSAGE, minutes = 1))

            assertThat(told.last().decisions)
                .contains(Decision.NotifyGuardian(GuardianEvent.StopShown(SensitiveActionKind.CODE_MESSAGE)))
            assertThat(notSetUp.last().decisions.filterIsInstance<Decision.NotifyGuardian>()).isEmpty()
        }

        @Test
        fun `is told when Continue is chosen, and only then`() {
            fun answer(choice: StopChoice) = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                StopAnswered(at(2), SensitiveActionKind.CODE_MESSAGE, choice),
                settings = withGuardian,
            ).last().decisions

            assertThat(answer(StopChoice.CONTINUE))
                .containsExactly(Decision.NotifyGuardian(GuardianEvent.StopOverridden(SensitiveActionKind.CODE_MESSAGE)))
            assertThat(answer(StopChoice.HANG_UP)).isEmpty()
            assertThat(answer(StopChoice.CALL_MY_BANK)).isEmpty()
            // Leaving the screen to hang up from the call screen is ordinary: no false alarm to the relative.
            assertThat(answer(StopChoice.LEFT)).isEmpty()
        }
    }

    @Nested
    inner class OutsideACall {

        @Test
        fun `a remote-control app installed outside a call is warned about`() {
            val steps = run(action(SensitiveActionKind.REMOTE_CONTROL_INSTALLED, minutes = 0))

            // The app it is about goes with it: the warning names it, the journal keeps which one it was.
            assertThat(steps.last().decisions)
                .containsExactly(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, source = "test"))
            assertThat(steps.last().journal)
                .containsExactly(JournalEntry.Warning(at(0).wall, WarningReason.REMOTE_CONTROL_INSTALLED, source = "test"))
        }

        @Test
        fun `during a call the same install is a STOP, not a warning`() {
            val steps = run(callStarted(), action(SensitiveActionKind.REMOTE_CONTROL_INSTALLED, minutes = 4))

            assertThat(steps.lastStops().single().trigger).isEqualTo(SensitiveActionKind.REMOTE_CONTROL_INSTALLED)
            assertThat(steps.last().decisions.filterIsInstance<Decision.ShowWarning>()).isEmpty()
        }
    }

    @Nested
    inner class LongCalls {

        @Test
        fun `a call with an unknown number asks to be woken at the threshold`() {
            val steps = run(callStarted())

            assertThat(steps.last().decisions).containsExactly(Decision.ScheduleTick(at(10)))
        }

        @Test
        fun `a hidden number is unknown, a contact or an unreadable caller is not`() {
            assertThat(run(callStarted(counterpart = Counterpart.Hidden)).last().decisions).hasSize(1)
            assertThat(run(callStarted(counterpart = contact)).last().decisions).isEmpty()
            assertThat(run(callStarted(counterpart = Counterpart.Unreadable)).last().decisions).isEmpty()
        }

        @Test
        fun `the reminder fires once per call, at the threshold`() {
            val settings = ProtectionSettings(guardianAlerts = true)
            val steps = run(callStarted(), Tick(at(10)), Tick(at(25)), settings = settings)

            assertThat(steps[1].decisions).containsExactly(
                Decision.ShowWarning(WarningReason.LONG_CALL_WITH_UNKNOWN),
                Decision.NotifyGuardian(GuardianEvent.LongCallWithUnknown),
            )
            assertThat(steps[1].journal).containsExactly(JournalEntry.Warning(at(10).wall, WarningReason.LONG_CALL_WITH_UNKNOWN))
            assertThat(steps[2].decisions).isEmpty()
        }

        @Test
        fun `an early tick asks again for the due time`() {
            val steps = run(callStarted(), Tick(at(4)))

            assertThat(steps.last().decisions).containsExactly(Decision.ScheduleTick(at(10)))
        }

        @Test
        fun `a tick after the call ended does nothing`() {
            val steps = run(callStarted(), CallEnded(at(3), CallChannel.CELLULAR), Tick(at(10)))

            assertThat(steps.last().decisions).isEmpty()
        }

        @Test
        fun `the next call gets its own reminder`() {
            val steps = run(
                callStarted(),
                Tick(at(10)),
                CallEnded(at(11), CallChannel.CELLULAR),
                callStarted(minutes = 30),
                Tick(at(40)),
            )

            assertThat(steps.last().decisions).contains(Decision.ShowWarning(WarningReason.LONG_CALL_WITH_UNKNOWN))
        }
    }

    @Nested
    inner class CallTracking {

        @Test
        fun `a second start on a busy channel keeps the first call`() {
            val steps = run(callStarted(), callStarted(minutes = 6, counterpart = contact))

            assertThat(steps.last().state.calls.getValue(CallChannel.CELLULAR).startedAt).isEqualTo(at(0))
            assertThat(steps.last().decisions).isEmpty()
        }

        @Test
        fun `an end with no call in progress changes nothing`() {
            val steps = run(CallEnded(at(1), CallChannel.CELLULAR))

            assertThat(steps.last().state).isEqualTo(DetectorState())
        }

        @Test
        fun `an end stamped a moment before the start is clamped to the start`() {
            val steps = run(callStarted(minutes = 5), CallEnded(at(4, 58), CallChannel.CELLULAR))

            assertThat(steps.last().state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(5))
        }

        @Test
        fun `an earlier call's end, repeated or late, does not end the call in progress`() {
            val steps = run(
                callStarted(),
                CallEnded(at(5), CallChannel.CELLULAR),
                callStarted(minutes = 6),
                CallEnded(at(5), CallChannel.CELLULAR),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 22),
            )

            assertThat(steps[3].state.calls.getValue(CallChannel.CELLULAR).isActive).isTrue()
            assertThat(steps.lastStops().single().call.startedAt).isEqualTo(at(6))
        }

        @Test
        fun `an ended call's start, repeated or late, does not revive it`() {
            val steps = run(
                callStarted(),
                CallEnded(at(5), CallChannel.CELLULAR),
                callStarted(minutes = 0),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 30),
            )

            assertThat(steps[2].state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(5))
            assertThat(steps.last().decisions).isEmpty()
        }

        @Test
        fun `a call whose end was missed stops counting after the maximum, and a new start replaces it`() {
            val max = CallWindow.MAX_TRACKED_CALL.toMinutes()
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = max + 1),
                callStarted(minutes = max + 2, counterpart = contact),
            )

            assertThat(steps[1].decisions).isEmpty()
            assertThat(steps[2].state.calls.getValue(CallChannel.CELLULAR).startedAt).isEqualTo(at(max + 2))
        }

        /**
         * Review of 2026-11-04 (M2): a missed call's end, hours after the cap, ended the lost call there and opened a
         * 15-minute window on a call that was not one — a false STOP on the user's own code.
         */
        @Test
        fun `an end that comes after the maximum closes the call at it, and opens no window`() {
            val max = CallWindow.MAX_TRACKED_CALL.toMinutes()
            val steps = run(
                callStarted(),
                CallEnded(at(max + 60), CallChannel.CELLULAR),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = max + 61),
            )

            assertThat(steps[1].state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(max))
            assertThat(steps[2].decisions).isEmpty()
            // Within the maximum, the end is the call's own: its window opens (the negative control).
            val withinCap = run(
                callStarted(),
                CallEnded(at(max - 1), CallChannel.CELLULAR),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = max),
            )
            assertThat(withinCap.lastStops()).hasSize(1)
        }
    }

    /** Measured on Android 16 (2026-11-02): a phone switched off mid-call reports no call state when it boots. */
    @Nested
    inner class Restarts {

        private val window = ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.toMinutes()

        @Test
        fun `a restart ends the call it interrupted, and the after-call window runs from the restart`() {
            val steps = run(
                callStarted(),
                PhoneRestarted(at(40)),
                action(SensitiveActionKind.BANK_VALIDATION_REQUEST, minutes = 40 + window),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 40 + window + 1),
            )

            assertThat(steps[1].state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(40))
            val stop = steps[2].decisions.filterIsInstance<Decision.ShowStop>().single()
            assertThat(stop.call.isActive).isFalse()
            // Without the restart, this one would still be "during a call", for up to 8 hours.
            assertThat(steps[3].decisions).isEmpty()
        }

        @Test
        fun `a call the 8-hour cap already closed is not revived by a later restart`() {
            val max = CallWindow.MAX_TRACKED_CALL.toMinutes()
            val steps = run(
                callStarted(),
                PhoneRestarted(at(max + 60)),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = max + 61),
            )

            // Closed at the cap, where its window is long over.
            assertThat(steps[1].state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(max))
            assertThat(steps[2].decisions).isEmpty()
        }

        @Test
        fun `a call that started after the restart is not ended by it`() {
            val steps = run(callStarted(minutes = 5), PhoneRestarted(at(4)))

            assertThat(steps[1].state.calls.getValue(CallChannel.CELLULAR).isActive).isTrue()
        }

        /** Within its 60-second hold, the screen the restart killed would otherwise absorb the next attempt. */
        @Test
        fun `a screen shown before a restart is gone - the action repeated right after it is stopped again`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1),
                PhoneRestarted(at(1, seconds = 20)),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 1, seconds = 40),
            )

            assertThat(steps[2].state.pendingStops).isEmpty()
            assertThat(steps.lastStops()).hasSize(1)
        }

        /** Reported again until recorded (a process killed in between): the second time must change nothing. */
        @Test
        fun `the same restart twice is the same state`() {
            val steps = run(
                callStarted(),
                action(SensitiveActionKind.CODE_MESSAGE, minutes = 50),
                PhoneRestarted(at(40)),
                PhoneRestarted(at(40)),
            )

            assertThat(steps[3].state).isEqualTo(steps[2].state)
        }

        @Test
        fun `a restart is tracked with protection off, like every call`() {
            val steps = run(callStarted(), PhoneRestarted(at(40)), settings = ProtectionSettings(enabled = false))

            assertThat(steps[1].state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(40))
        }
    }

    @Nested
    inner class ProtectionOff {

        private val off = ProtectionSettings(enabled = false)

        @Test
        fun `decides nothing but keeps tracking calls`() {
            val steps = run(callStarted(), action(SensitiveActionKind.CODE_MESSAGE, minutes = 1), Tick(at(10)), settings = off)

            assertThat(steps.flatMap { it.decisions }).isEmpty()
            assertThat(steps.flatMap { it.journal }).isEmpty()
            assertThat(steps.last().state.calls).containsKey(CallChannel.CELLULAR)
        }

        @Test
        fun `turned back on mid-call, it knows the call`() {
            val state = engine.process(DetectorState(), callStarted(), off).state

            val evaluation = engine.process(state, action(SensitiveActionKind.CODE_MESSAGE, minutes = 2), ProtectionSettings())

            assertThat(evaluation.decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        }
    }
}
