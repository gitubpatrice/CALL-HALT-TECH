package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.FakePhoneClock
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.SensorQueue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/** An answer is carried out — the call ended when it must be — and reaches the engine's journal through the queue. */
class StopAnswersTest {

    private val store = InMemoryDetectorStore()
    private val calls = FakeCallControl()

    @Test
    fun `hanging up ends the call, and the answer is journaled`() = runTest {
        assertThat(answers().answer(session(onCall = true), StopChoice.HANG_UP)).isTrue()
        drain()
        assertThat(calls.ended).isEqualTo(1)
        assertThat(store.journal.single()).isInstanceOf(JournalEntry.Answer::class.java)
    }

    /** D-011: during the call, "Call my bank" ends it (the dialler opens after, in the screen); after it, nothing to end. */
    @Test
    fun `calling the bank ends the call during it, and nothing after it`() = runTest {
        answers().answer(session(onCall = true), StopChoice.CALL_MY_BANK)
        answers().answer(session(onCall = false), StopChoice.CALL_MY_BANK)
        drain()
        assertThat(calls.ended).isEqualTo(1)
    }

    @Test
    fun `continuing ends nothing, and is journaled too`() = runTest {
        answers().answer(session(onCall = true), StopChoice.CONTINUE)
        drain()
        assertThat(calls.ended).isEqualTo(0)
        assertThat(store.journal).hasSize(1)
    }

    /** A screen left open after its call ended: nothing to end, and nothing is — the answer still counts. */
    @Test
    fun `with no call left, hanging up ends nothing and is journaled`() = runTest {
        calls.noCall = true
        assertThat(answers().answer(session(onCall = true), StopChoice.HANG_UP)).isTrue()
        drain()
        assertThat(calls.ended).isEqualTo(0)
        assertThat(store.journal).hasSize(1)
    }

    /** M3: told the call is cut while the scammer is still on the line — the worst. The screen must stay. */
    @Test
    fun `a call the app could not end is not an answer`() = runTest {
        calls.succeeds = false
        assertThat(answers().answer(session(onCall = true), StopChoice.HANG_UP)).isFalse()
        drain()
        assertThat(store.journal).isEmpty()
    }

    /** H2: a screen that absorbed two STOPs answers both. */
    @Test
    fun `the answer counts for every trigger the screen stood for`() = runTest {
        val two = StopSession.of(request(onCall = true))
            .replacedBy(StopRequest(2, SensitiveActionKind.BANK_APP_OPENED, onCall = true, CallChannel.CELLULAR))
        answers().answer(two, StopChoice.CONTINUE)
        drain()
        assertThat(store.journal.map { (it as JournalEntry.Answer).trigger })
            .containsExactly(SensitiveActionKind.CODE_MESSAGE, SensitiveActionKind.BANK_APP_OPENED)
    }

    /** H1: left without an answer, the screen frees its triggers, journaled as such. */
    @Test
    fun `a screen left without an answer frees its triggers`() = runTest {
        answers().left(session(onCall = true))
        drain()
        assertThat((store.journal.single() as JournalEntry.Answer).choice).isEqualTo(StopChoice.LEFT)
        assertThat(calls.ended).isEqualTo(0)
    }

    private val processSignal = ProcessSignalUseCase(
        DetectionEngine(),
        store,
        { ProtectionSettings() },
        RestartSource { null },
        AndroidDecisionExecutor { true },
    )

    private val clock = FakePhoneClock(Moment(Instant.EPOCH))

    private fun TestScope.answers() = StopAnswers(SensorQueue(backgroundScope), { processSignal }, calls, clock)

    private fun request(onCall: Boolean) = StopRequest(1, SensitiveActionKind.CODE_MESSAGE, onCall, CallChannel.CELLULAR)

    private fun session(onCall: Boolean) = StopSession.of(request(onCall))

    /** Runs the queue's work: its consumer lives in `backgroundScope`, which `advanceUntilIdle()` would not run. */
    private fun TestScope.drain() = testScheduler.runCurrent()

    private class FakeCallControl : CallControl {
        var succeeds = true
        var noCall = false
        var ended = 0

        override fun canEndCall() = true

        override fun noCallGoingOn() = noCall

        override fun endCall(): Boolean {
            ended++
            return succeeds
        }
    }
}
