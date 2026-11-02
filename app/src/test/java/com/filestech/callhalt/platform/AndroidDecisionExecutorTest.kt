package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/** The executor's contract (Ports.kt): every STOP first; it throws only when a STOP did not reach the user. */
class AndroidDecisionExecutorTest {

    private val call = CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable, Instant.EPOCH)
    private val stop = Decision.ShowStop(SensitiveActionKind.CODE_MESSAGE, call)
    private val others = listOf(Decision.ScheduleTick(Instant.EPOCH), Decision.ShowWarning(WarningReason.LONG_CALL_WITH_UNKNOWN))

    @Test
    fun `a STOP that reached the front is a success`() = runTest {
        val shown = mutableListOf<Decision.ShowStop>()
        AndroidDecisionExecutor { asked ->
            shown += asked
            true
        }.execute(others + stop)
        assertThat(shown).containsExactly(stop)
    }

    /** Android blocks a background start without a word: the engine must journal it as NOT shown. */
    @Test
    fun `a STOP that did not reach the front is reported by an exception`() = runTest {
        assertThrows<IllegalStateException> { AndroidDecisionExecutor { false }.execute(others + stop) }
    }

    /** Thrown for anything else, it would make a STOP that was shown read as one that was not. */
    @Test
    fun `the decisions it does not carry out yet throw nothing, and show no screen`() = runTest {
        AndroidDecisionExecutor { error("no STOP to show") }.execute(others)
    }
}
