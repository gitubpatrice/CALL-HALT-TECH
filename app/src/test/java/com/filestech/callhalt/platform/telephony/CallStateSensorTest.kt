package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.Uptime
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.InMemoryPreferences
import com.filestech.callhalt.platform.noGuardian
import com.filestech.callhalt.platform.noWakeUps
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * Sequences of PHONE_STATE broadcasts, through the sensor's memory, its translation and the REAL engine: what
 * `callSignal` relies on the engine for ("it absorbs a repeat", "a waiting call changes nothing") is proven here,
 * not taken from a comment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallStateSensorTest {

    private val store = InMemoryDetectorStore()

    /** Call states alone never decide a STOP: a launcher asked for one fails the test. */
    private val noStop = StopLauncher { error("no STOP expected") }
    private val noWarning = WarningPoster { error("no warning expected") }
    private val settings = SettingsSource { ProtectionSettings() }
    private val events = CallEvents()
    private val sensor = CallStateSensor(
        PhoneStateMemory(InMemoryPreferences()),
        ProcessSignalUseCase(
            DetectionEngine(),
            store,
            settings,
            { null },
            { null },
            AndroidDecisionExecutor(noStop, noWarning, noGuardian, noWakeUps),
        ),
        events,
    )

    /** What the STOP screen listens to, to stop offering "Hang up" once its call is over. */
    @Test
    fun `an idle phone is told once the engine has it, a ringing or answered one is not`() = runTest {
        // What the engine held at the moment each end was told: the order, not only the outcome.
        val callActiveWhenTold = mutableListOf<Boolean>()
        val listening = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.endedCalls.collect { channel -> callActiveWhenTold += store.state.calls.getValue(channel).isActive }
        }
        feed(PhoneState.RINGING, PhoneState.OFFHOOK)
        assertThat(callActiveWhenTold).isEmpty()
        feed(PhoneState.IDLE, from = 10)
        assertThat(callActiveWhenTold).containsExactly(false)
        listening.cancel()
    }

    @Test
    fun `a call with a second one waiting, and every broadcast twice, is one incoming call`() = runTest {
        feed(
            PhoneState.IDLE,
            PhoneState.RINGING,
            PhoneState.OFFHOOK, // answered: the call starts (2 s)
            PhoneState.OFFHOOK, // the same broadcast, twice
            PhoneState.RINGING, // a second call, waiting
            PhoneState.OFFHOOK,
            PhoneState.IDLE, // the call ends (6 s)
            PhoneState.IDLE,
        )
        assertThat(store.state.calls).containsExactly(
            CallChannel.CELLULAR,
            CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable, startedAt = at(2), endedAt = at(6)),
        )
    }

    @Test
    fun `a dialled call is outgoing`() = runTest {
        feed(PhoneState.IDLE, PhoneState.OFFHOOK, PhoneState.IDLE)
        assertThat(store.state.calls.getValue(CallChannel.CELLULAR))
            .isEqualTo(CallSession(CallChannel.CELLULAR, CallDirection.OUTGOING, Counterpart.Unreadable, at(1), at(2)))
    }

    @Test
    fun `a call missed or declined is no call`() = runTest {
        feed(PhoneState.IDLE, PhoneState.RINGING, PhoneState.IDLE)
        assertThat(store.state.calls).isEmpty()
    }

    /** A second call rang during the first, was not answered, and the first ended meanwhile: the end is not lost. */
    @Test
    fun `the end of a call is not lost behind a waiting call nobody answered`() = runTest {
        feed(PhoneState.IDLE, PhoneState.RINGING, PhoneState.OFFHOOK, PhoneState.RINGING, PhoneState.IDLE)
        assertThat(store.state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(at(4))
    }

    /** The phone restarted mid-call: no IDLE ever came for that call, and the memory still says OFFHOOK. */
    @Test
    fun `after a restart mid-call, the next call is tracked and its end closes it`() = runTest {
        feed(PhoneState.IDLE, PhoneState.OFFHOOK)
        feed(PhoneState.RINGING, PhoneState.OFFHOOK, PhoneState.IDLE, from = 10_000)
        // Within the 8-hour cap, the engine takes it for the same conversation and keeps the first start: the call
        // is tracked all the same, and its end closes it — nothing left open, no STOP lost.
        assertThat(store.state.calls.getValue(CallChannel.CELLULAR).isActive).isFalse()
    }

    /**
     * The limit found by the review of 2026-11-02 (M1), closed by P-13: the civil clock steps back between two calls,
     * and the second call's start, stamped before the first one's end, was taken for a repeat of the first. On the
     * uptime, the second call comes after; on the civil clock alone — a phone that does not count its boots — it is
     * still lost, and the after-call window of the first covers only the second's first 15 minutes.
     */
    @Test
    fun `a clock stepped back between two calls - the second is tracked, on the uptime`() = runTest {
        feed(PhoneState.IDLE, PhoneState.OFFHOOK, PhoneState.IDLE, uptime = true) // call 1 ends at 2 s
        // Call 2, three seconds after on the uptime, stamped 1 s by a civil clock set back.
        sensor.onPhoneState(PhoneState.OFFHOOK, Moment(T0.plusSeconds(1), uptime(seconds = 5)))
        assertThat(store.state.calls.getValue(CallChannel.CELLULAR).isActive).isTrue()
    }

    @Test
    fun `a clock stepped back between two calls - on the civil clock alone, the second is lost`() = runTest {
        feed(PhoneState.IDLE, PhoneState.OFFHOOK, PhoneState.IDLE) // call 1 ends at 2 s
        sensor.onPhoneState(PhoneState.OFFHOOK, at(1)) // call 2, stamped 1 s by a clock set back
        assertThat(store.state.calls.getValue(CallChannel.CELLULAR).isActive).isFalse()
    }

    /** Feeds [states] one second apart, from [from] seconds after T0 — on the civil clock alone, or with the uptime. */
    private suspend fun feed(vararg states: PhoneState, from: Long = 0, uptime: Boolean = false) {
        states.forEachIndexed { index, state ->
            val seconds = from + index
            sensor.onPhoneState(state, if (uptime) Moment(T0.plusSeconds(seconds), uptime(seconds)) else at(seconds))
        }
    }

    private fun at(seconds: Long) = Moment(T0.plusSeconds(seconds))

    private fun uptime(seconds: Long) = Uptime(boot = 3, sinceBoot = Duration.ofSeconds(3_600 + seconds))

    private companion object {
        val T0: Instant = Instant.parse("2026-11-02T09:00:00Z")
    }
}
