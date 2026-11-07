package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.FakePhoneClock
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.noGuardian
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import javax.inject.Provider

/** FN-1, D-035: the call going on when the process starts, told before any broadcast. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallStateAtStartTest {

    private val t0 = Moment(Instant.parse("2026-11-07T10:07:00Z"))
    private val store = InMemoryDetectorStore()
    private val clock = FakePhoneClock(t0)
    private var resolved = false

    private val processSignal = Provider {
        resolved = true
        ProcessSignalUseCase(
            DetectionEngine(),
            store,
            SettingsSource { ProtectionSettings() },
            RestartSource { null },
            LiveCallSource { null },
            AndroidDecisionExecutor(
                StopLauncher { error("no STOP expected") },
                WarningPoster { error("no warning expected") },
                noGuardian,
            ),
        )
    }

    /** As the phone's read says a call goes on: answered before the first unlock (measured). */
    private val callOn = LiveCallSource { at -> CallStarted(at, CallChannel.CELLULAR, CallDirection.UNKNOWN, Counterpart.Unreadable) }

    @Test
    fun `a call going on at the start is told, at the start's moment, and nothing runs on the caller`() = runTest {
        val start = CallStateAtStart(SensorQueue(backgroundScope), clock, callOn, processSignal)

        start.check()
        clock.now = t0.plus(Duration.ofSeconds(30)) // the queue's turn comes later: the moment is the start's
        assertThat(resolved).isFalse()
        drain()

        val call = store.state.calls.getValue(CallChannel.CELLULAR)
        assertThat(call.isActive).isTrue()
        assertThat(call.startedAt).isEqualTo(t0)
    }

    @Test
    fun `a state the phone cannot tell at the start changes nothing`() = runTest {
        val start = CallStateAtStart(SensorQueue(backgroundScope), clock, LiveCallSource { null }, processSignal)

        start.check()
        drain()

        assertThat(store.state.calls).isEmpty()
        assertThat(resolved).isFalse()
    }

    private fun TestScope.drain() = testScheduler.runCurrent()
}
