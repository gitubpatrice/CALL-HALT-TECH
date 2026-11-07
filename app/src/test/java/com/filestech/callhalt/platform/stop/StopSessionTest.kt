package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The rules of the STOP screen, without the screen (general review of 2026-11-02: H2, M2, M3, double tap). */
class StopSessionTest {

    private val code = StopRequest(1, SensitiveActionKind.CODE_MESSAGE, onCall = true, CallChannel.CELLULAR)
    private val bankApp = StopRequest(2, SensitiveActionKind.BANK_APP_OPENED, onCall = true, CallChannel.CELLULAR)

    /** H2: otherwise the replaced trigger stays "shown" and absorbs its repeats for a minute. */
    @Test
    fun `a STOP replaced by another keeps its trigger, and the screen shows the new one`() {
        val session = StopSession.of(code).replacedBy(bankApp)
        assertThat(session.current).isEqualTo(bankApp)
        assertThat(session.triggers).containsExactly(SensitiveActionKind.CODE_MESSAGE, SensitiveActionKind.BANK_APP_OPENED)
    }

    @Test
    fun `the end of its call stops the screen saying you are on the phone, another channel's does not`() {
        val session = StopSession.of(code)
        assertThat(session.afterCallEnded(CallChannel.VOIP).current.onCall).isTrue()
        assertThat(session.afterCallEnded(CallChannel.CELLULAR).current.onCall).isFalse()
        assertThat(session.afterNoCallLeft().current.onCall).isFalse()
    }

    /** M2: a later call — the user calling the real bank — is never offered to be cut by a screen that saw the end. */
    @Test
    fun `once its call has ended, the screen never goes back to it by itself`() {
        val ended = StopSession.of(code).afterCallEnded(CallChannel.CELLULAR)
        assertThat(ended.afterCallEnded(CallChannel.CELLULAR).current.onCall).isFalse()
        assertThat(ended.afterNoCallLeft().current.onCall).isFalse()
    }

    /** The scammer calling back: a NEW STOP decided during that call may offer to cut it. */
    @Test
    fun `a new STOP during a new call brings the call back on screen`() {
        val ended = StopSession.of(code).afterCallEnded(CallChannel.CELLULAR)
        assertThat(ended.replacedBy(bankApp).current.onCall).isTrue()
    }

    @Test
    fun `answering and a failed hang-up are remembered, across a replacement`() {
        val session = StopSession.of(code).afterCallNotEnded().answering().replacedBy(bankApp)
        assertThat(session.answered).isTrue()
        assertThat(session.callStillOn).isTrue()
    }

    /**
     * A STOP arriving while the phone asks for the unlock (the first screen answered, its page waiting): a screen of
     * its own, whose buttons answer it, with nothing to open over it (review of 2026-11-07: it was lost).
     */
    @Test
    fun `a STOP arriving once the screen is answered is a screen of its own`() {
        val waiting = StopSession.of(code).answering(opening = StopChoice.REMOVE_ACCESS)
        val next = waiting.receiving(bankApp)
        assertThat(next.current).isEqualTo(bankApp)
        assertThat(next.triggers).containsExactly(SensitiveActionKind.BANK_APP_OPENED)
        assertThat(next.answered).isFalse()
        assertThat(next.opening).isNull()
    }

    @Test
    fun `a STOP arriving before the answer takes the screen and joins its triggers`() {
        val session = StopSession.of(code)
        assertThat(session.receiving(bankApp)).isEqualTo(session.replacedBy(bankApp))
    }

    /** Two unlock callbacks, or a rebuilt screen asking again: the page opens once. */
    @Test
    fun `the page waiting for the unlock opens once`() {
        val waiting = StopSession.of(code).answering(opening = StopChoice.CALL_MY_BANK)
        assertThat(waiting.opening).isEqualTo(StopChoice.CALL_MY_BANK)
        assertThat(waiting.opened().opening).isNull()
        assertThat(waiting.opened().answered).isTrue()
        assertThat(StopSession.of(code).answering().opening).isNull()
    }
}
