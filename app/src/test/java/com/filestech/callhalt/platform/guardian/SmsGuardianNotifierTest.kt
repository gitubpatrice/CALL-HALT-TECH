package com.filestech.callhalt.platform.guardian

import com.filestech.callhalt.data.guardian.Guardian
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.platform.FakePhoneClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The relative set up is texted what happened, once per kind of alert, and only what the phone took is counted. */
@OptIn(ExperimentalCoroutinesApi::class)
class SmsGuardianNotifierTest {

    private val clock = FakePhoneClock(Moment(Instant.parse("2026-11-07T10:00:00Z")))
    private var guardian: Guardian? = Guardian("0612345678", "Julie")
    private val texts = mutableListOf<Pair<String, String>>()
    private var phoneTakes = true

    /** What the phone answers once it tried: false, no network. */
    private var outcome = true
    private val sender = object : TextSender {
        override fun canSend() = phoneTakes

        override fun send(to: String, text: String): Deferred<Boolean>? {
            if (!phoneTakes) return null
            texts += to to text
            return CompletableDeferred(outcome)
        }
    }
    private val code = GuardianEvent.StopShown(SensitiveActionKind.CODE_MESSAGE, duringCall = true)

    private fun TestScope.notifier() =
        SmsGuardianNotifier({ guardian }, sender, clock, { "${it.template}:${it.what}" }, backgroundScope)

    @Test
    fun `the relative set up is texted what happened`() = runTest {
        notifier().tell(code)
        val expected = guardianText(code)!!
        assertThat(texts).containsExactly("0612345678" to "${expected.template}:${expected.what}")
    }

    /** The relative removed after the engine decided: no one to tell. */
    @Test
    fun `no relative, no text`() = runTest {
        guardian = null
        notifier().tell(code)
        assertThat(texts).isEmpty()
    }

    @Test
    fun `the same alert within a quarter of an hour is texted once`() = runTest {
        val notifier = notifier()
        notifier.tell(code)
        clock.now = clock.now.plus(Duration.ofMinutes(3))
        notifier.tell(code)
        notifier.tell(GuardianEvent.StopOverridden(SensitiveActionKind.CODE_MESSAGE, duringCall = true))
        assertThat(texts).hasSize(2)
    }

    /** The permission taken back: once given again, the next alert is not held back by a text never sent. */
    @Test
    fun `a text the phone did not take holds the next one back for nothing`() = runTest {
        val notifier = notifier()
        phoneTakes = false
        notifier.tell(code)
        phoneTakes = true
        notifier.tell(code)
        assertThat(texts).hasSize(1)
    }

    /** No network at the first STOP: the next alert of the kind is texted, not held for a quarter of an hour. */
    @Test
    fun `a text that did not go does not hold the next one back`() = runTest {
        val notifier = notifier()
        outcome = false
        notifier.tell(code)
        testScheduler.runCurrent()
        outcome = true
        clock.now = clock.now.plus(Duration.ofMinutes(1))
        notifier.tell(code)
        assertThat(texts).hasSize(2)
    }

    @Test
    fun `a long call sends nothing yet`() = runTest {
        notifier().tell(GuardianEvent.LongCallWithUnknown)
        assertThat(texts).isEmpty()
    }
}
