package com.filestech.callhalt.platform.telephony

import android.telephony.TelephonyManager
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Moment
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

    /** The state read right now (FN-1) names the same three states; a value the platform does not document is unknown. */
    @Test
    fun `the call state read right now is the broadcast's, anything else unknown`() {
        assertThat(phoneStateOf(TelephonyManager.CALL_STATE_IDLE)).isEqualTo(PhoneState.IDLE)
        assertThat(phoneStateOf(TelephonyManager.CALL_STATE_RINGING)).isEqualTo(PhoneState.RINGING)
        assertThat(phoneStateOf(TelephonyManager.CALL_STATE_OFFHOOK)).isEqualTo(PhoneState.OFFHOOK)
        assertThat(phoneStateOf(UNDOCUMENTED_STATE)).isNull()
    }

    /**
     * Review of 2026-11-07, F1: a call through an app's own call service (WhatsApp) is told neither way — whether the
     * broadcast counts it is not settled, and counted here alone, no broadcast would end it.
     */
    @Test
    fun `the state worth telling is no call at all, or the SIM's answered with none ringing`() {
        val offhook = TelephonyManager.CALL_STATE_OFFHOOK
        val ringing = TelephonyManager.CALL_STATE_RINGING
        assertWithMessage("no call at all").that(liveState(inCall = false, inManagedCall = false, callState = 0))
            .isEqualTo(PhoneState.IDLE)
        assertWithMessage("the SIM's, answered").that(liveState(inCall = true, inManagedCall = true, callState = offhook))
            .isEqualTo(PhoneState.OFFHOOK)
        assertWithMessage("the SIM's, ringing").that(liveState(inCall = true, inManagedCall = true, callState = ringing))
            .isNull()
        assertWithMessage("an app's own call alone").that(liveState(inCall = true, inManagedCall = false, callState = offhook))
            .isNull()
        assertWithMessage("an unknown state").that(liveState(inCall = true, inManagedCall = true, callState = UNDOCUMENTED_STATE))
            .isNull()
    }

    /** GPT review of 2026-11-07, 2: three reads one after the other; a call starting or ending between them. */
    @Test
    fun `reads that disagree tell nothing, never a call's end`() {
        val idle = TelephonyManager.CALL_STATE_IDLE
        val offhook = TelephonyManager.CALL_STATE_OFFHOOK
        listOf(
            Triple(false, true, offhook),
            Triple(false, false, offhook),
            Triple(false, true, idle),
            Triple(true, true, idle),
            Triple(true, false, idle),
        ).forEach { (inCall, inManagedCall, callState) ->
            assertWithMessage("inCall=$inCall managed=$inManagedCall state=$callState")
                .that(liveState(inCall, inManagedCall, callState)).isNull()
        }
    }

    @Test
    fun `ringing is no call, nor does it end one`() {
        (PhoneState.entries + null).forEach { previous ->
            assertWithMessage("after $previous").that(callSignal(previous, PhoneState.RINGING, AT)).isNull()
        }
    }

    private companion object {
        val AT = Moment(Instant.parse("2026-11-02T09:00:00Z"))

        /** Not one of the three: what a future platform might send. */
        const val UNDOCUMENTED_STATE = 7
    }
}
