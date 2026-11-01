package com.filestech.callhalt.domain.usecase

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.Evaluation
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.port.DetectorStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class ProcessSignalUseCaseTest {

    private val t0: Instant = Instant.parse("2026-11-01T10:00:00Z")

    /** A saved contact: the call asks for no long-call wake-up, so the executor is first called for the STOP. */
    private val contact = Counterpart.Number("+33698765432", inContacts = true)

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
        val process = ProcessSignalUseCase(DetectionEngine(), store, { ProtectionSettings() }, { executed += it })

        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        process(SensitiveAction(t0.plusSeconds(90), SensitiveActionKind.CODE_MESSAGE))

        assertThat(executed.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        assertThat(store.journal).hasSize(1)
    }

    @Test
    fun `nothing to do means the executor is not called`() = runTest {
        var calls = 0
        val process = ProcessSignalUseCase(DetectionEngine(), MemoryStore(), { ProtectionSettings() }, { calls++ })

        process(SensitiveAction(t0, SensitiveActionKind.CODE_MESSAGE))

        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `a STOP the executor could not show is journaled as such, and the next attempt is stopped again`() = runTest {
        val store = MemoryStore()
        var failNext = true
        val shown = mutableListOf<Decision>()
        val process = ProcessSignalUseCase(DetectionEngine(), store, { ProtectionSettings() }) { decisions ->
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
        val process = ProcessSignalUseCase(DetectionEngine(), store, { ProtectionSettings() }) {
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

    @Test
    fun `the settings are read for each signal`() = runTest {
        var settings = ProtectionSettings(enabled = false)
        val executed = mutableListOf<Decision>()
        val process = ProcessSignalUseCase(DetectionEngine(), MemoryStore(), { settings }, { executed += it })

        process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        settings = ProtectionSettings()
        process(SensitiveAction(t0.plusSeconds(30), SensitiveActionKind.BANK_APP_OPENED))

        assertThat(executed.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }
}
