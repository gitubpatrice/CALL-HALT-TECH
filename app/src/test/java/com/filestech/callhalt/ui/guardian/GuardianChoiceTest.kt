package com.filestech.callhalt.ui.guardian

import com.filestech.callhalt.data.guardian.Guardian
import com.filestech.callhalt.data.guardian.GuardianRepository
import com.filestech.callhalt.platform.FailingPreferences
import com.filestech.callhalt.platform.InMemoryPreferences
import com.filestech.callhalt.platform.guardian.ChoiceTexts
import com.filestech.callhalt.platform.guardian.TextSender
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The relative's screen (D-034): chosen, asked for the permission, texted; replaced or removed, told so. */
@OptIn(ExperimentalCoroutinesApi::class)
class GuardianChoiceTest {

    private val repository = GuardianRepository(InMemoryPreferences())
    private var granted = true
    private var network = true
    private val texts = mutableListOf<Pair<String, String>>()
    private val sender = object : TextSender {
        override fun canSend() = granted

        override fun send(to: String, text: String): Deferred<Boolean>? {
            if (!granted) return null
            texts += to to text
            return CompletableDeferred(network)
        }
    }
    private val words = object : ChoiceTexts {
        override fun setUp() = SET_UP

        override fun removed() = REMOVED
    }
    private val julie = Guardian("0612345678", "Julie")
    private val paul = Guardian("0698765432", "Paul")

    private fun TestScope.choice(store: GuardianRepository = repository) = GuardianChoice(this, store, sender, words)

    private suspend fun GuardianChoice.now(): GuardianState = state.first()

    @Test
    fun `a contact picked is kept and texted that they will be alerted`() = runTest {
        val choice = choice()
        choice.on(GuardianMove.Picked(julie))
        advanceUntilIdle()
        assertThat(repository.current()).isEqualTo(julie)
        assertThat(texts).containsExactly("0612345678" to SET_UP)
        assertThat(choice.now().step).isEqualTo(GuardianStep.Sent(julie))
    }

    @Test
    fun `a number typed is kept as the app texts it`() = runTest {
        val choice = choice()
        choice.on(GuardianMove.Typed("06 12 34 56 78"))
        advanceUntilIdle()
        assertThat(repository.current()).isEqualTo(Guardian("0612345678"))
    }

    /** A short number could cost money at each STOP; a contact with no usable number is no relative either. */
    @Test
    fun `what the app may not text is refused, nothing kept nor sent`() = runTest {
        val choice = choice()
        choice.on(GuardianMove.Typed("36111"))
        advanceUntilIdle()
        assertThat(choice.now().step).isEqualTo(GuardianStep.InvalidNumber)
        choice.on(GuardianMove.Picked(null))
        advanceUntilIdle()
        assertThat(choice.now().step).isEqualTo(GuardianStep.InvalidNumber)
        assertThat(repository.current()).isNull()
        assertThat(texts).isEmpty()
    }

    @Test
    fun `without the permission the phone asks for it, and the relative is set up once granted`() = runTest {
        granted = false
        val choice = choice()
        choice.on(GuardianMove.Picked(julie))
        advanceUntilIdle()
        assertThat(choice.now().step).isEqualTo(GuardianStep.AskPermission(julie))
        choice.on(GuardianMove.PermissionAsked)
        assertThat(choice.now().step).isEqualTo(GuardianStep.AskPermission(julie, asked = true))
        granted = true
        choice.on(GuardianMove.PermissionAnswered)
        advanceUntilIdle()
        assertThat(repository.current()).isEqualTo(julie)
        assertThat(choice.now().step).isEqualTo(GuardianStep.Sent(julie))
    }

    /** Refused, then granted in the settings: back in front, the relative refused for it is set up. */
    @Test
    fun `refused, then granted in the settings, the relative is set up on return`() = runTest {
        granted = false
        val choice = choice()
        choice.on(GuardianMove.Picked(julie))
        choice.on(GuardianMove.PermissionAnswered)
        advanceUntilIdle()
        assertThat(choice.now().step).isEqualTo(GuardianStep.PermissionRefused(julie))
        assertThat(repository.current()).isNull()
        granted = true
        choice.on(GuardianMove.Resumed)
        advanceUntilIdle()
        assertThat(repository.current()).isEqualTo(julie)
    }

    /** No network: the relative stays set up — the alerts will try again — and the screen says the text did not go. */
    @Test
    fun `a first text that does not go leaves the relative set up, and can be sent again`() = runTest {
        network = false
        val choice = choice()
        choice.on(GuardianMove.Picked(julie))
        advanceUntilIdle()
        assertThat(repository.current()).isEqualTo(julie)
        assertThat(choice.now().step).isEqualTo(GuardianStep.NotSent(julie))
        network = true
        choice.on(GuardianMove.Resend)
        advanceUntilIdle()
        assertThat(choice.now().step).isEqualTo(GuardianStep.Sent(julie))
        assertThat(texts).hasSize(2)
    }

    /** "Remove your daughter's number", said on the phone: she learns it. */
    @Test
    fun `a relative replaced is told they no longer will be alerted`() = runTest {
        repository.set(julie)
        val choice = choice()
        choice.on(GuardianMove.Picked(paul))
        advanceUntilIdle()
        assertThat(repository.current()).isEqualTo(paul)
        assertThat(texts).containsExactly("0612345678" to REMOVED, "0698765432" to SET_UP).inOrder()
    }

    /** The same number, or written the international way: the same person, no "you will no longer be alerted". */
    @Test
    fun `the same relative chosen again gets no farewell`() = runTest {
        repository.set(julie)
        val choice = choice()
        choice.on(GuardianMove.Typed("06 12 34 56 78"))
        advanceUntilIdle()
        choice.on(GuardianMove.Typed("+33 6 12 34 56 78"))
        advanceUntilIdle()
        assertThat(texts).containsExactly("0612345678" to SET_UP, "+33612345678" to SET_UP).inOrder()
    }

    @Test
    fun `a relative removed is forgotten, then told`() = runTest {
        repository.set(julie)
        val choice = choice()
        choice.on(GuardianMove.AskRemove)
        assertThat(choice.now().step).isEqualTo(GuardianStep.ConfirmRemove)
        choice.on(GuardianMove.Remove)
        advanceUntilIdle()
        assertThat(repository.current()).isNull()
        assertThat(texts).containsExactly("0612345678" to REMOVED)
        assertThat(choice.now().step).isEqualTo(GuardianStep.Removed)
    }

    /** Two quick touches on "Alert this number", or on "Stop alerting": one text each, not two (GPT review, 3). */
    @Test
    fun `a double touch texts once`() = runTest {
        repository.set(julie)
        val choice = choice()
        choice.on(GuardianMove.Picked(paul))
        choice.on(GuardianMove.Picked(paul))
        advanceUntilIdle()
        assertThat(texts).containsExactly("0612345678" to REMOVED, "0698765432" to SET_UP).inOrder()

        texts.clear()
        choice.on(GuardianMove.AskRemove)
        choice.on(GuardianMove.Remove)
        choice.on(GuardianMove.Remove)
        advanceUntilIdle()
        assertThat(texts).containsExactly("0698765432" to REMOVED)
    }

    @Test
    fun `removing can be cancelled`() = runTest {
        repository.set(julie)
        val choice = choice()
        choice.on(GuardianMove.AskRemove)
        choice.on(GuardianMove.Cancel)
        assertThat(choice.now().step).isEqualTo(GuardianStep.Idle)
        assertThat(repository.current()).isEqualTo(julie)
    }

    /** A choice the app could not keep is said, and nothing is texted that would claim otherwise. */
    @Test
    fun `a relative the app could not keep is said, and nobody is texted`() = runTest {
        val choice = choice(GuardianRepository(FailingPreferences(java.io.IOException("disk full"))))
        choice.on(GuardianMove.Picked(julie))
        advanceUntilIdle()
        assertThat(choice.now().step).isEqualTo(GuardianStep.SaveFailed)
        assertThat(texts).isEmpty()
    }

    private companion object {
        const val SET_UP = "set up"
        const val REMOVED = "removed"
    }
}
