package com.filestech.callhalt.ui.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.history.Alert
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The words of the history (D-041), with Android's own resources, in whatever language the phone is in: what the screen
 * shows and what is shared are the same lines.
 */
@RunWith(AndroidJUnit4::class)
class HistoryTextOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resources = context.resources
    private val text = HistoryText(resources)
    private val at = Instant.parse("2026-11-09T09:44:05Z")

    @Test
    fun aStopSaysWhatItWasWhereItCameFromAndEachAnswerInOrder() {
        val stop = Alert.Stop(
            at = at,
            trigger = SensitiveActionKind.CODE_MESSAGE,
            source = "com.google.android.apps.messaging",
            answers = listOf(StopChoice.LEFT, StopChoice.HANG_UP),
        )

        val lines = text.lines(stop)

        assertThat(lines).hasSize(4)
        assertThat(lines[1]).isEqualTo(resources.getString(R.string.history_stop, resources.getString(R.string.guardian_sms_what_code)))
        assertThat(lines[2]).isEqualTo(resources.getString(R.string.history_app, "com.google.android.apps.messaging"))
        val answers = resources.getString(R.string.history_answer_left) + resources.getString(R.string.history_then) +
            resources.getString(R.string.stop_hang_up)
        assertThat(lines[3]).isEqualTo(resources.getString(R.string.history_answer, answers))
        // The separator itself, as read: its quotes only keep its spaces, Android drops them (GPT review of 2026-11-09, 1).
        val then = resources.getString(R.string.history_then)
        assertThat(then).doesNotContain("\"")
        assertThat(then).matches(", \\S.* ")
    }

    /** The date in the phone's zone, with that zone's offset: what a bank elsewhere needs to read it. */
    @Test
    fun theDateSaysItsOffset() {
        val date = text.lines(Alert.Stop(at, SensitiveActionKind.CODE_MESSAGE, source = null)).first()

        assertThat(date).matches(".+ \\(UTC([+-]\\d\\d:\\d\\d)?\\)")
    }

    /** A remote-control app by our list's name; a STOP the phone blocked, said; one unanswered, said. */
    @Test
    fun aRemoteAppIsNamed_aBlockedOrUnansweredScreenIsSaid() {
        val installed = Alert.Stop(at, SensitiveActionKind.REMOTE_CONTROL_INSTALLED, "com.carriez.flutter_hbb", notShown = true)
        val unanswered = Alert.Stop(at, SensitiveActionKind.SMS_BURST, source = null)

        assertThat(text.lines(installed)).containsAtLeast(
            resources.getString(R.string.history_app, "RustDesk"),
            resources.getString(R.string.history_not_shown),
        )
        assertThat(text.lines(installed)).doesNotContain(resources.getString(R.string.history_no_answer))
        assertThat(text.lines(unanswered).last()).isEqualTo(resources.getString(R.string.history_no_answer))
    }

    @Test
    fun theSharedTextHasItsHeadingThenEachAlert() {
        val warning = Alert.Warning(at, WarningReason.REMOTE_CONTROL_INSTALLED, "com.anydesk.anydeskandroid")
        val stop = Alert.Stop(at, SensitiveActionKind.BANK_VALIDATION_REQUEST, "com.credit_coop.android.mobilebanking")

        val shared = text.shared(listOf(warning, stop))

        assertThat(shared).startsWith(resources.getString(R.string.history_share_heading) + "\n\n")
        assertThat(shared).contains(resources.getString(R.string.warning_remote_installed_title, "AnyDesk"))
        assertThat(shared).contains("com.credit_coop.android.mobilebanking")
        assertThat(shared.split("\n\n")).hasSize(3)
    }
}
