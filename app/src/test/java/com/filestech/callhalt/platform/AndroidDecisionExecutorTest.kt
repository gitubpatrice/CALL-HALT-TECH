package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.WarningReason
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

    @Test
    fun `a STOP that reached the front is a success`() = runTest {
        val shown = mutableListOf<Decision.ShowStop>()
        val launcher = { asked: Decision.ShowStop ->
            shown += asked
            true
        }
        AndroidDecisionExecutor(launcher, warnings).execute(others + stop)
        assertThat(shown).containsExactly(stop)
    }

    /** Android blocks a background start without a word: the engine must journal it as NOT shown. */
    @Test
    fun `a STOP that did not reach the front is reported by an exception`() = runTest {
        assertThrows<IllegalStateException> { AndroidDecisionExecutor({ false }, warnings).execute(others + stop) }
    }

    @Test
    fun `a warning is handed to the notification`() = runTest {
        AndroidDecisionExecutor({ error("no STOP to show") }, warnings).execute(others)
        assertThat(posted).containsExactly(warning)
    }

    /** A STOP shown with it must not read as not shown because the phone threw while posting the warning. */
    @Test
    fun `a warning that throws leaves a shown STOP a success`() = runTest {
        AndroidDecisionExecutor({ true }) { error("notification service gone") }.execute(others + stop)
    }

    /** Thrown for anything else, it would make a STOP that was shown read as one that was not. */
    @Test
    fun `the decisions it does not carry out, or could not, throw nothing, and show no screen`() = runTest {
        AndroidDecisionExecutor({ error("no STOP to show") }) { false }.execute(others)
    }
}
