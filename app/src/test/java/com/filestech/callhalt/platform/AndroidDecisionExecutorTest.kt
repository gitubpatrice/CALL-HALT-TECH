package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.WarningReason
import com.filestech.callhalt.platform.guardian.GuardianNotifier
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/** The executor's contract (Ports.kt): every STOP first; it throws only when a STOP did not reach the user. */
class AndroidDecisionExecutorTest {

    private val call = CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable, Moment(Instant.EPOCH))
    private val stop = Decision.ShowStop(SensitiveActionKind.CODE_MESSAGE, call)
    private val warning = Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, "com.anydesk.anydeskandroid")
    private val others = listOf(Decision.ScheduleTick(Moment(Instant.EPOCH)), warning)
    private val posted = mutableListOf<Decision.ShowWarning>()
    private val warnings = WarningPoster { asked ->
        posted += asked
        true
    }
    private val tell = Decision.NotifyGuardian(GuardianEvent.StopShown(SensitiveActionKind.CODE_MESSAGE, duringCall = true))

    @Test
    fun `a STOP that reached the front is a success`() = runTest {
        val shown = mutableListOf<Decision.ShowStop>()
        val launcher = { asked: Decision.ShowStop ->
            shown += asked
            true
        }
        AndroidDecisionExecutor(launcher, warnings, noGuardian).execute(others + stop)
        assertThat(shown).containsExactly(stop)
    }

    /** Android blocks a background start without a word: the engine must journal it as NOT shown. */
    @Test
    fun `a STOP that did not reach the front is reported by an exception`() = runTest {
        assertThrows<IllegalStateException> { AndroidDecisionExecutor({ false }, warnings, noGuardian).execute(others + stop) }
    }

    @Test
    fun `a warning is handed to the notification`() = runTest {
        AndroidDecisionExecutor({ error("no STOP to show") }, warnings, noGuardian).execute(others)
        assertThat(posted).containsExactly(warning)
    }

    /** A STOP shown with it must not read as not shown because the phone threw while posting the warning. */
    @Test
    fun `a warning that throws leaves a shown STOP a success`() = runTest {
        AndroidDecisionExecutor({ true }, { error("notification service gone") }, noGuardian).execute(others + stop)
    }

    /** The STOP first: the screen is what stops the scam, the text only tells someone. */
    @Test
    fun `the relative is told after the STOP is shown`() = runTest {
        val order = mutableListOf<String>()
        val launcher = { _: Decision.ShowStop ->
            order += "stop"
            true
        }
        val guardian = GuardianNotifier { event -> order += "text ${(event as GuardianEvent.StopShown).trigger}" }
        AndroidDecisionExecutor(launcher, warnings, guardian).execute(listOf(tell, stop))
        assertThat(order).containsExactly("stop", "text CODE_MESSAGE").inOrder()
    }

    /** A STOP shown with it must not read as not shown because the phone threw while texting. */
    @Test
    fun `a text that throws leaves a shown STOP a success`() = runTest {
        AndroidDecisionExecutor({ true }, warnings) { error("no SMS on this phone") }.execute(listOf(stop, tell))
    }

    /** Thrown for anything else, it would make a STOP that was shown read as one that was not. */
    @Test
    fun `the decisions it does not carry out, or could not, throw nothing, and show no screen`() = runTest {
        AndroidDecisionExecutor({ error("no STOP to show") }, { false }, noGuardian).execute(others)
    }
}
