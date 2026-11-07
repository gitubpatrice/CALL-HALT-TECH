package com.filestech.callhalt.domain.usecase

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.Evaluation
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.domain.port.DetectorStore
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.Restart
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class ProcessSignalUseCaseTest {

    private val t0 = Moment(Instant.parse("2026-11-01T10:00:00Z"))

    private fun Moment.plusSeconds(seconds: Long) = plus(Duration.ofSeconds(seconds))

    /** A saved contact: the call asks for no long-call wake-up, so the executor is first called for the STOP. */
    private val contact = Counterpart.Number("+33698765432", inContacts = true)

    private val noRestart = RestartSource { null }

    private val noLiveCall = LiveCallSource { null }

    /** The use case with a fresh engine; each test names only what it changes. */
    private fun useCase(
        store: DetectorStore = MemoryStore(),
        restarts: RestartSource = noRestart,
        liveCall: LiveCallSource = noLiveCall,
        settings: SettingsSource = SettingsSource { ProtectionSettings() },
        executor: DecisionExecutor = DecisionExecutor {},
    ) = ProcessSignalUseCase(DetectionEngine(), store, settings, restarts, liveCall, executor)

    /** An in-memory store with the contract's shape: step on the committed state, commit, return decisions. */
    private class MemoryStore : DetectorStore {
        var state = DetectorState()
        val journal = mutableListOf<JournalEntry>()

        override suspend fun apply(step: (DetectorState) -> Evaluation): List<Decision> {
            val evaluation = step(state)
            state = evaluation.state
            journal.addAll(evaluation.journal)
            return evaluation.decisions
        }
    }

    @Test
    fun `the state survives from one signal to the next, and the decisions are carried out`() = runTest {
        val store = MemoryStore()
        val executed = mutableListOf<Decision>()
        val process = useCase(store = store) { executed += it }

        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        process(SensitiveAction(t0.plusSeconds(90), SensitiveActionKind.CODE_MESSAGE))

        assertThat(executed.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        assertThat(store.journal).hasSize(1)
    }

    @Test
    fun `nothing to do means the executor is not called`() = runTest {
        var calls = 0
        val process = useCase { calls++ }

        process(SensitiveAction(t0, SensitiveActionKind.CODE_MESSAGE))

        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `a STOP the executor could not show is journaled as such, and the next attempt is stopped again`() = runTest {
        val store = MemoryStore()
        var failNext = true
        val shown = mutableListOf<Decision>()
        val process = useCase(store = store) { decisions ->
            if (failNext) {
                failNext = false
                error("the screen could not start")
            }
            shown += decisions
        }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        val failure = try {
            process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))
            null
        } catch (expected: IllegalStateException) {
            expected
        }
        // "The code did not go through, read it again", 20 s later: inside the hold of the screen nobody saw.
        process(SensitiveAction(t0.plusSeconds(80), SensitiveActionKind.CODE_MESSAGE))

        assertThat(failure).isNotNull()
        assertThat(shown.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        assertThat(store.journal.map { it::class }).containsExactly(
            JournalEntry.Stop::class,
            JournalEntry.NotShown::class,
            JournalEntry.Stop::class,
        ).inOrder()
    }

    @Test
    fun `a cancellation goes through untouched - nothing is reported as not shown`() = runTest {
        val store = MemoryStore()
        val process = useCase(store = store) {
            throw CancellationException("receiver window over")
        }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        val cancelled = try {
            process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))
            null
        } catch (expected: CancellationException) {
            expected
        }

        assertThat(cancelled).isNotNull()
        assertThat(store.journal.map { it::class }).containsExactly(JournalEntry.Stop::class)
    }

    /** Asked by the one entry point, so that no adapter can forget it. */
    @Test
    fun `the restarts are asked for before every signal`() = runTest {
        var asked = 0
        val restarts = RestartSource {
            asked++
            null
        }
        val process = useCase(restarts = restarts)

        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))
        process(SensitiveAction(t0.plusSeconds(30), SensitiveActionKind.CODE_MESSAGE))

        assertThat(asked).isEqualTo(2)
    }

    /** The phone's call state right now, as the app turns it into a signal (TelephonyLiveCall): a call on, none, or unknown. */
    private fun live(on: Boolean?) = LiveCallSource { at ->
        when (on) {
            true -> CallStarted(at, CallChannel.CELLULAR, CallDirection.UNKNOWN, Counterpart.Unreadable)
            false -> CallEnded(at, CallChannel.CELLULAR)
            null -> null
        }
    }

    /**
     * FN-1, measured on the Android 16 emulator: after a restart, a call answered before the first unlock reaches the
     * app by no broadcast. The phone says it goes on: the action is one during a call.
     */
    @Test
    fun `a call no broadcast told is found when an action comes, and the STOP is one during that call`() = runTest {
        val store = MemoryStore()
        val executed = mutableListOf<Decision>()
        val process = useCase(store = store, liveCall = live(true)) { executed += it }

        process(SensitiveAction(t0, SensitiveActionKind.BANK_VALIDATION_REQUEST))

        val stop = executed.filterIsInstance<Decision.ShowStop>().single()
        assertThat(stop.call.isActive).isTrue()
        assertThat(stop.call.startedAt).isEqualTo(t0)
    }

    /** The broadcast told it: the phone's answer is the same conversation, whose start the window keeps. */
    @Test
    fun `a call the engine knows keeps its start when the phone says it goes on`() = runTest {
        val store = MemoryStore()
        val process = useCase(store = store, liveCall = live(true))
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        process(SensitiveAction(t0.plusSeconds(600), SensitiveActionKind.CODE_MESSAGE))

        val call = store.state.calls.getValue(CallChannel.CELLULAR)
        assertThat(call.startedAt).isEqualTo(t0)
        assertThat(call.direction).isEqualTo(CallDirection.INCOMING)
        assertThat(call.isActive).isTrue()
    }

    /** The end no broadcast told (the app stopped): the action is one just after the call, not during it for hours. */
    @Test
    fun `an end no broadcast told is found when an action comes`() = runTest {
        val store = MemoryStore()
        val executed = mutableListOf<Decision>()
        val process = useCase(store = store, liveCall = live(false)) { executed += it }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        process(SensitiveAction(t0.plusSeconds(600), SensitiveActionKind.CODE_MESSAGE))

        val stop = executed.filterIsInstance<Decision.ShowStop>().single()
        assertThat(stop.call.isActive).isFalse()
        assertThat(stop.call.endedAt).isEqualTo(t0.plusSeconds(600))
    }

    /** Ringing, or the state unreadable: nothing is guessed, and the engine keeps what it knows. */
    @Test
    fun `a state the phone cannot tell changes nothing`() = runTest {
        val store = MemoryStore()
        val executed = mutableListOf<Decision>()
        val process = useCase(store = store, liveCall = live(null)) { executed += it }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        process(SensitiveAction(t0.plusSeconds(600), SensitiveActionKind.CODE_MESSAGE))
        process(SensitiveAction(t0.plusSeconds(700), SensitiveActionKind.BANK_VALIDATION_REQUEST))

        assertThat(executed.filterIsInstance<Decision.ShowStop>().map { it.call.isActive }).containsExactly(true, true)
    }

    /** Review of 2026-11-07, M1: a read that throws must not lose the action it comes before — that STOP is its purpose. */
    @Test
    fun `a call state that cannot be read never loses the action`() = runTest {
        val store = MemoryStore()
        val executed = mutableListOf<Decision>()
        val broken = LiveCallSource { error("telephony service restarting") }
        val process = useCase(store = store, liveCall = broken) { executed += it }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        process(SensitiveAction(t0.plusSeconds(600), SensitiveActionKind.CODE_MESSAGE))

        assertThat(executed.filterIsInstance<Decision.ShowStop>().single().call.isActive).isTrue()
    }

    /** GPT review of 2026-11-07, 3: the commit of what was read fails; the action is still handled. */
    @Test
    fun `a call state read that cannot be committed never loses the action`() = runTest {
        val memory = MemoryStore()
        var applies = 0
        val failingOnce = object : DetectorStore {
            override suspend fun apply(step: (DetectorState) -> Evaluation): List<Decision> {
                applies++
                if (applies == 2) error("database locked") // the call state read before the action
                return memory.apply(step)
            }
        }
        val executed = mutableListOf<Decision>()
        val process = useCase(store = failingOnce, liveCall = live(true)) { executed += it }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        process(SensitiveAction(t0.plusSeconds(600), SensitiveActionKind.CODE_MESSAGE))

        assertThat(executed.filterIsInstance<Decision.ShowStop>().single().call.isActive).isTrue()
    }

    /** Asked before a sensitive action only: a call signal is the broadcast itself, and the screen's answer needs none. */
    @Test
    fun `the phone's call state is asked before a sensitive action only`() = runTest {
        var asked = 0
        val liveCall = LiveCallSource {
            asked++
            null
        }
        val process = useCase(liveCall = liveCall)

        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))
        process(SensitiveAction(t0.plusSeconds(30), SensitiveActionKind.CODE_MESSAGE))
        process(CallEnded(t0.plusSeconds(60), CallChannel.CELLULAR))

        assertThat(asked).isEqualTo(1)
    }

    /**
     * The restart first: it ends the earlier boot's call, which the phone cannot report, before the call going on now
     * is told — told first, the restart would end it.
     */
    @Test
    fun `the call going on is told after the restart, which would otherwise end it`() = runTest {
        val store = MemoryStore()
        val restarts = FakeRestarts(store)
        val executed = mutableListOf<Decision>()
        val process = useCase(store = store, restarts = restarts, liveCall = live(true)) { executed += it }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        val restartedAt = t0.plusSeconds(300)
        restarts.unreported = restartedAt
        process(SensitiveAction(restartedAt.plusSeconds(120), SensitiveActionKind.BANK_VALIDATION_REQUEST))

        val stop = executed.filterIsInstance<Decision.ShowStop>().single()
        assertThat(stop.call.isActive).isTrue()
        assertThat(stop.call.startedAt).isEqualTo(restartedAt.plusSeconds(120))
    }

    /** A source that reports [unreported] until recorded, and remembers what the engine held when it was. */
    private class FakeRestarts(private val store: MemoryStore) : RestartSource {
        var unreported: Moment? = null
        val callEndedWhenRecorded = mutableListOf<Moment?>()

        override suspend fun unreported(): Restart? = unreported?.let { at ->
            Restart(at) {
                callEndedWhenRecorded += store.state.calls[CallChannel.CELLULAR]?.endedAt
                unreported = null
            }
        }
    }

    /** The phone switched off mid-call, then restarted: no call state came at boot (measured on Android 16). */
    @Test
    fun `a restart reported before a signal ends the call it interrupted, before that signal looks for one`() = runTest {
        val store = MemoryStore()
        val restarts = FakeRestarts(store)
        val executed = mutableListOf<Decision>()
        val restartedAt = t0.plusSeconds(3_600)
        val process = useCase(store = store, restarts = restarts) { executed += it }
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact))

        restarts.unreported = restartedAt
        val window = ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW
        process(SensitiveAction(restartedAt.plus(window).plusSeconds(1), SensitiveActionKind.BANK_VALIDATION_REQUEST))
        process(SensitiveAction(restartedAt.plus(window).plusSeconds(2), SensitiveActionKind.CODE_MESSAGE))

        assertThat(store.state.calls.getValue(CallChannel.CELLULAR).endedAt).isEqualTo(restartedAt)
        // Without the restart, both would have been "during a call" for up to 8 hours.
        assertThat(executed).isEmpty()
        // Recorded once, and only once the engine had committed it: a process killed before reports it again.
        assertThat(restarts.callEndedWhenRecorded).containsExactly(restartedAt)
    }

    /**
     * Review of 2026-11-02: the boot's time is computed with the clock at the time of the question. Corrected forward
     * since the call's start was stamped, it would end the ghost after that start, and absorb the real call into it.
     */
    @Test
    fun `a restart is never placed after the signal that follows it`() = runTest {
        val store = MemoryStore()
        val restarts = FakeRestarts(store)
        val process = useCase(store = store, restarts = restarts)
        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, contact)) // the ghost

        val realCallAt = t0.plusSeconds(3_600)
        restarts.unreported = realCallAt.plusSeconds(5)
        process(CallStarted(realCallAt, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))

        val call = store.state.calls.getValue(CallChannel.CELLULAR)
        assertThat(call.startedAt).isEqualTo(realCallAt)
        assertThat(call.isActive).isTrue()
    }

    @Test
    fun `the settings are read for each signal`() = runTest {
        var settings = ProtectionSettings(enabled = false)
        val executed = mutableListOf<Decision>()
        val process = useCase(settings = SettingsSource { settings }) { executed += it }

        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        settings = ProtectionSettings()
        process(SensitiveAction(t0.plusSeconds(30), SensitiveActionKind.BANK_APP_OPENED))

        assertThat(executed.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }
}
