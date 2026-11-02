package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.WarningReason
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/** Until the STOP screen exists, a STOP must read as NOT shown: the executor's contract says so by throwing. */
class AndroidDecisionExecutorTest {

    private val executor = AndroidDecisionExecutor()
    private val call = CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable, Instant.EPOCH)

    @Test
    fun `a STOP it cannot show is reported by an exception`() = runTest {
        assertThrows<IllegalStateException> {
            executor.execute(listOf(Decision.ScheduleTick(Instant.EPOCH), Decision.ShowStop(SensitiveActionKind.CODE_MESSAGE, call)))
        }
    }

    /** Thrown for anything else, it would make a STOP that was shown read as one that was not. */
    @Test
    fun `the decisions it does not carry out yet throw nothing`() = runTest {
        executor.execute(listOf(Decision.ScheduleTick(Instant.EPOCH), Decision.ShowWarning(WarningReason.LONG_CALL_WITH_UNKNOWN)))
    }
}
