package com.filestech.callhalt.platform.guardian

import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/** The relative is told what was shown or chosen, during the call or just after it, and what happened. */
class GuardianTextTest {

    @Test
    fun `a STOP shown says during the call or just after it`() {
        SensitiveActionKind.entries.forEach { trigger ->
            assertWithMessage(trigger.name).that(guardianText(GuardianEvent.StopShown(trigger, duringCall = true))?.template)
                .isEqualTo(R.string.guardian_sms_stop_during_call)
            assertWithMessage(trigger.name).that(guardianText(GuardianEvent.StopShown(trigger, duringCall = false))?.template)
                .isEqualTo(R.string.guardian_sms_stop_after_call)
        }
    }

    @Test
    fun `Continue chosen says during the call or just after it`() {
        SensitiveActionKind.entries.forEach { trigger ->
            assertWithMessage(trigger.name).that(guardianText(GuardianEvent.StopOverridden(trigger, duringCall = true))?.template)
                .isEqualTo(R.string.guardian_sms_continue_during_call)
            assertWithMessage(trigger.name).that(guardianText(GuardianEvent.StopOverridden(trigger, duringCall = false))?.template)
                .isEqualTo(R.string.guardian_sms_continue_after_call)
        }
    }

    /** One text per trigger, the same whatever was shown or chosen: the relative is never told one thing for another. */
    @Test
    fun `each trigger says what happened, in words of its own`() {
        val whats = SensitiveActionKind.entries.map { trigger ->
            val shown = guardianText(GuardianEvent.StopShown(trigger, duringCall = true))?.what
            val overridden = guardianText(GuardianEvent.StopOverridden(trigger, duringCall = false))?.what
            assertWithMessage(trigger.name).that(overridden).isEqualTo(shown)
            shown
        }
        assertThat(whats.toSet()).hasSize(SensitiveActionKind.entries.size)
        assertThat(guardianText(GuardianEvent.StopShown(SensitiveActionKind.CODE_MESSAGE, duringCall = true))?.what)
            .isEqualTo(R.string.guardian_sms_what_code)
    }

    /** Its reminder waits for the number to be read: no text that no call can bring today. */
    @Test
    fun `a long call has no text yet`() {
        assertThat(guardianText(GuardianEvent.LongCallWithUnknown)).isNull()
    }
}
