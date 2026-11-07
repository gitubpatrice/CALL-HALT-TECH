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
    fun `a STOP replaced by another keeps its trigger, and the new request takes the screen`() {
        val session = StopSession.of(code).replacedBy(bankApp)
        assertThat(session.current).isEqualTo(bankApp)
        assertThat(session.triggers).containsExactly(SensitiveActionKind.CODE_MESSAGE, SensitiveActionKind.BANK_APP_OPENED)
    }

    private val access = StopRequest(3, SensitiveActionKind.ACCESSIBILITY_ENABLED, onCall = true, CallChannel.CELLULAR)
    private val remote = StopRequest(4, SensitiveActionKind.REMOTE_CONTROL_OPENED, onCall = true, CallChannel.CELLULAR)

    /**
     * Publication audit, N-1: the code the scammer texts on purpose while the accessibility STOP shows must not take its
     * "Remove the access" away — the access would stay, and its STOP never comes back.
     */
    @Test
    fun `the screen speaks of its gravest trigger, whatever came after`() {
        assertThat(StopSession.of(access).replacedBy(code).shown).isEqualTo(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(StopSession.of(code).replacedBy(access).shown).isEqualTo(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(StopSession.of(remote).replacedBy(code).replacedBy(bankApp).shown)
            .isEqualTo(SensitiveActionKind.REMOTE_CONTROL_OPENED)
        assertThat(StopSession.of(remote).replacedBy(access).replacedBy(code).shown)
            .isEqualTo(SensitiveActionKind.ACCESSIBILITY_ENABLED)
    }

    @Test
    fun `among triggers of the same weight, the screen speaks of the newest`() {
        assertThat(StopSession.of(code).shown).isEqualTo(SensitiveActionKind.CODE_MESSAGE)
        assertThat(StopSession.of(code).replacedBy(bankApp).shown).isEqualTo(SensitiveActionKind.BANK_APP_OPENED)
        assertThat(StopSession.of(bankApp).replacedBy(code).shown).isEqualTo(SensitiveActionKind.CODE_MESSAGE)
    }

    /** Review of 2026-11-07, 2: installed, then opened, then a code — the newest of the two graver ones. */
    @Test
    fun `among equals, the newest by arrival, not by the order of the enum`() {
        val installed = StopRequest(5, SensitiveActionKind.REMOTE_CONTROL_INSTALLED, onCall = true, CallChannel.CELLULAR)
        assertThat(StopSession.of(installed).replacedBy(remote).replacedBy(code).shown)
            .isEqualTo(SensitiveActionKind.REMOTE_CONTROL_OPENED)
        assertThat(StopSession.of(remote).replacedBy(installed).replacedBy(code).shown)
            .isEqualTo(SensitiveActionKind.REMOTE_CONTROL_INSTALLED)
    }

    /**
     * Review of 2026-11-07, 1: the phone blocked the screen, so the STOP came as a notification; a code replaced it before
     * any touch. The screen it opens stands for both, and speaks of the accessibility.
     */
    @Test
    fun `a screen opened from a notification that stood for several speaks of the gravest`() {
        val fromNotification = code.copy(earlier = listOf(SensitiveActionKind.ACCESSIBILITY_ENABLED))
        val session = StopSession.of(fromNotification)
        assertThat(session.shown).isEqualTo(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(session.triggers).containsExactly(SensitiveActionKind.ACCESSIBILITY_ENABLED, SensitiveActionKind.CODE_MESSAGE)

        val reached = StopSession.of(bankApp).replacedBy(fromNotification)
        assertThat(reached.shown).isEqualTo(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(reached.triggers).containsExactly(
            SensitiveActionKind.BANK_APP_OPENED,
            SensitiveActionKind.ACCESSIBILITY_ENABLED,
            SensitiveActionKind.CODE_MESSAGE,
        ).inOrder()
    }

    /** Its trigger is the gravest; its call state stays the newest request's (the scammer calling back). */
    @Test
    fun `the gravest trigger keeps the newest call state`() {
        val session = StopSession.of(access.copy(onCall = false)).replacedBy(code)
        assertThat(session.shown).isEqualTo(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(session.current.onCall).isTrue()
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
    fun `answering is remembered across a replacement`() {
        val session = StopSession.of(code).answering().replacedBy(bankApp)
        assertThat(session.answered).isTrue()
    }

    /**
     * Gemini review of 2026-11-07, 4: "Hang up" failed, the scammer hung up and called back, a new STOP comes — its call
     * is one to offer to cut. A lasting failure (the permission withdrawn) hides the button anyway.
     */
    @Test
    fun `a failed hang-up hides the call buttons until a new STOP brings its own call`() {
        val failed = StopSession.of(code).afterCallNotEnded()
        assertThat(failed.callStillOn).isTrue()
        assertThat(failed.replacedBy(bankApp).callStillOn).isFalse()
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
