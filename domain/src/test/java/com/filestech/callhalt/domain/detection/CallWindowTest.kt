package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Moment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class CallWindowTest {

    private val t0 = Moment(Instant.parse("2026-11-01T10:00:00Z"))
    private val window: Duration = Duration.ofMinutes(15)

    private fun call(channel: CallChannel, startMinute: Long, endMinute: Long? = null) = CallSession(
        channel = channel,
        direction = CallDirection.INCOMING,
        counterpart = Counterpart.Hidden,
        startedAt = t0.plus(Duration.ofMinutes(startMinute)),
        endedAt = endMinute?.let { t0.plus(Duration.ofMinutes(it)) },
    )

    private fun stateOf(vararg calls: CallSession) = DetectorState(calls = calls.associateBy { it.channel })

    @Test
    fun `an ended call on one channel and a live one on the other - the earliest-started is returned`() {
        val cellular = call(CallChannel.CELLULAR, startMinute = 0, endMinute = 5)
        val voip = call(CallChannel.VOIP, startMinute = 3)

        assertThat(CallWindow.callAt(stateOf(cellular, voip), t0.plus(Duration.ofMinutes(10)), window)).isEqualTo(cellular)
    }

    @Test
    fun `once the ended call is out of its window, the live one still counts`() {
        val cellular = call(CallChannel.CELLULAR, startMinute = 0, endMinute = 5)
        val voip = call(CallChannel.VOIP, startMinute = 3)

        assertThat(CallWindow.callAt(stateOf(cellular, voip), t0.plus(Duration.ofMinutes(30)), window)).isEqualTo(voip)
    }

    @Test
    fun `in progress means started, not ended, and younger than the maximum - bounds included`() {
        val live = call(CallChannel.CELLULAR, startMinute = 0)

        assertThat(CallWindow.inProgress(live, t0)).isTrue()
        assertThat(CallWindow.inProgress(live, t0.plus(CallWindow.MAX_TRACKED_CALL))).isTrue()
        assertThat(CallWindow.inProgress(live, t0.plus(CallWindow.MAX_TRACKED_CALL).plus(Duration.ofSeconds(1)))).isFalse()
        assertThat(CallWindow.inProgress(live, t0.minus(Duration.ofSeconds(1)))).isFalse()
        assertThat(CallWindow.inProgress(call(CallChannel.CELLULAR, 0, 1), t0)).isFalse()
    }

    @Test
    fun `no call, no window`() {
        assertThat(CallWindow.callAt(DetectorState(), t0, window)).isNull()
    }
}
