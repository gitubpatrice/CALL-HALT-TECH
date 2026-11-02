package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.time.Instant

class PhoneStateTest {

    @Test
    fun `the three values the platform sends are read, anything else is ignored`() {
        assertThat(PhoneState.fromExtra("IDLE")).isEqualTo(PhoneState.IDLE)
        assertThat(PhoneState.fromExtra("RINGING")).isEqualTo(PhoneState.RINGING)
        assertThat(PhoneState.fromExtra("OFFHOOK")).isEqualTo(PhoneState.OFFHOOK)
        assertThat(PhoneState.fromExtra(null)).isNull()
        assertThat(PhoneState.fromExtra("offhook")).isNull()
        assertThat(PhoneState.fromExtra("")).isNull()
    }

    @Test
    fun `an answered call is incoming, a dialled one outgoing`() {
        assertThat(callSignal(PhoneState.RINGING, PhoneState.OFFHOOK, AT))
            .isEqualTo(CallStarted(AT, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable))
        assertThat(callSignal(PhoneState.IDLE, PhoneState.OFFHOOK, AT))
            .isEqualTo(CallStarted(AT, CallChannel.CELLULAR, CallDirection.OUTGOING, Counterpart.Unreadable))
    }

    /** Taking the memory on trust would lose the call after a restart mid-call; the engine absorbs a repeat. */
    @Test
    fun `off the hook always starts a call, its direction unknown when nothing tells it`() {
        listOf(PhoneState.OFFHOOK, null).forEach { previous ->
            assertWithMessage("after $previous").that(callSignal(previous, PhoneState.OFFHOOK, AT))
                .isEqualTo(CallStarted(AT, CallChannel.CELLULAR, CallDirection.UNKNOWN, Counterpart.Unreadable))
        }
    }

    /** Even after RINGING: during a call, a second one rang, was not answered, and the first call ended. */
    @Test
    fun `idle always ends the call, whatever came before`() {
        (PhoneState.entries + null).forEach { previous ->
            assertWithMessage("after $previous").that(callSignal(previous, PhoneState.IDLE, AT))
                .isEqualTo(CallEnded(AT, CallChannel.CELLULAR))
        }
    }

    @Test
    fun `ringing is no call, nor does it end one`() {
        (PhoneState.entries + null).forEach { previous ->
            assertWithMessage("after $previous").that(callSignal(previous, PhoneState.RINGING, AT)).isNull()
        }
    }

    private companion object {
        val AT: Instant = Instant.parse("2026-11-02T09:00:00Z")
    }
}
