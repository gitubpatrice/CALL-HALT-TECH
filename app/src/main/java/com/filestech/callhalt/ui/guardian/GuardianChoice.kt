package com.filestech.callhalt.ui.guardian

import com.filestech.callhalt.data.guardian.Guardian
import com.filestech.callhalt.data.guardian.GuardianRepository
import com.filestech.callhalt.data.guardian.guardianNumber
import com.filestech.callhalt.data.guardian.sameSubscriber
import com.filestech.callhalt.platform.guardian.ChoiceTexts
import com.filestech.callhalt.platform.guardian.TextSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** What the relative's screen says after the user's last move. */
sealed interface GuardianStep {
    data object Idle : GuardianStep

    data object InvalidNumber : GuardianStep

    data object NoContactsApp : GuardianStep

    /** [pending] chosen: the phone is to ask for the permission to send texts — [asked] once its dialog is shown. */
    data class AskPermission(val pending: Guardian, val asked: Boolean = false) : GuardianStep

    /** Refused: [pending] is set up once it is granted, here or in the settings. */
    data class PermissionRefused(val pending: Guardian) : GuardianStep

    data class Sending(val to: Guardian) : GuardianStep

    data class Sent(val to: Guardian) : GuardianStep

    data class NotSent(val to: Guardian) : GuardianStep

    data object ConfirmRemove : GuardianStep

    data object Removed : GuardianStep

    data object SaveFailed : GuardianStep
}

/** What the user does on the relative's screen, or what the phone answers. */
sealed interface GuardianMove {
    /** Back in front: the permission may have been granted in the settings meanwhile. */
    data object Resumed : GuardianMove

    data class Typed(val text: String) : GuardianMove

    /** A contact picked; null when it has no number the app may text, or is not from the phone's contacts. */
    data class Picked(val contact: Guardian?) : GuardianMove

    data object NoContactsApp : GuardianMove

    /** The phone's dialog is shown: asked once, not again when the screen is drawn anew (a rotation). */
    data object PermissionAsked : GuardianMove

    /** The phone's dialog answered: the permission is read, not the answer — a dialog not shown answers "refused". */
    data object PermissionAnswered : GuardianMove

    /** Refused once: the phone's dialog again — it may answer at once, refused for good; the settings then. */
    data object AskAgain : GuardianMove

    data object Resend : GuardianMove

    data object AskRemove : GuardianMove

    data object Cancel : GuardianMove

    data object Remove : GuardianMove
}

/** [guardian] set up; [canSend], the permission granted — without it, no alert reaches them. */
data class GuardianState(val guardian: Guardian?, val canSend: Boolean, val step: GuardianStep = GuardianStep.Idle)

/**
 * The relative's screen without Android (docs/ARCHITECTURE.md, D-034): the user picks a contact or types a number; the
 * app asks for the permission to send texts when it lacks it, keeps the relative, then texts them that they will be
 * alerted (Patrice, 2026-11-07) — and the relative they replace, or the one removed, that they no longer will.
 *
 * The relative is kept BEFORE the first text: a text that does not go (no network) leaves the protection set up, and
 * the screen says so, with a way to send it again. Removed, the relative is forgotten before the farewell, which is not
 * waited for: the user's choice is done whatever happens to the text. With the permission taken back since, removing
 * sends no farewell — a limit until the relative's code (PROJET.md, § 4.6).
 *
 * [scope] is the application's, not the screen's: leaving the screen right after a choice must not cancel its text once
 * the relative is saved (GPT review of 2026-11-07, 4).
 */
internal class GuardianChoice(
    private val scope: CoroutineScope,
    private val repository: GuardianRepository,
    private val sender: TextSender,
    private val texts: ChoiceTexts,
) {

    private val step = MutableStateFlow<GuardianStep>(GuardianStep.Idle)
    private val canSend = MutableStateFlow(sender.canSend())

    val state: Flow<GuardianState> = combine(repository.guardian, canSend, step, ::GuardianState)

    fun initial(): GuardianState = GuardianState(null, canSend.value)

    fun on(move: GuardianMove) {
        when (move) {
            is GuardianMove.Typed -> picked(guardianNumber(move.text)?.let(::Guardian))
            is GuardianMove.Picked -> picked(move.contact)
            GuardianMove.NoContactsApp -> step.value = GuardianStep.NoContactsApp
            GuardianMove.AskRemove -> step.value = GuardianStep.ConfirmRemove
            GuardianMove.Cancel -> step.value = GuardianStep.Idle
            // Once: a second touch on the dialog's button, before it closes, would text a second farewell.
            GuardianMove.Remove -> if (step.value == GuardianStep.ConfirmRemove) remove()
            GuardianMove.Resumed, GuardianMove.PermissionAsked, GuardianMove.PermissionAnswered, GuardianMove.AskAgain,
            GuardianMove.Resend,
            -> answer(move, step.value)
        }
    }

    /** The moves that answer the step the screen is at: outside it (a late answer, a second touch), nothing. */
    private fun answer(move: GuardianMove, now: GuardianStep) {
        when (move) {
            GuardianMove.Resumed -> permissionRead((now as? GuardianStep.PermissionRefused)?.pending)
            GuardianMove.PermissionAnswered -> permissionRead((now as? GuardianStep.AskPermission)?.pending)
            GuardianMove.PermissionAsked -> if (now is GuardianStep.AskPermission) step.value = now.copy(asked = true)
            GuardianMove.AskAgain -> if (now is GuardianStep.PermissionRefused) step.value = GuardianStep.AskPermission(now.pending)
            GuardianMove.Resend -> if (now is GuardianStep.NotSent) setUpAgain(now.to)
            else -> Unit
        }
    }

    /** The permission read again; [waiting], the relative chosen without it: set up once granted, else said refused. */
    private fun permissionRead(waiting: Guardian?) {
        canSend.value = sender.canSend()
        when {
            waiting == null -> Unit
            canSend.value -> save(waiting)
            else -> step.value = GuardianStep.PermissionRefused(waiting)
        }
    }

    /** While a choice is being saved and texted, another is nothing: a double touch would text twice (GPT review, 3). */
    private fun picked(guardian: Guardian?) {
        when {
            step.value is GuardianStep.Sending -> Unit
            guardian == null -> step.value = GuardianStep.InvalidNumber
            sender.canSend() -> save(guardian)
            else -> step.value = GuardianStep.AskPermission(guardian)
        }
    }

    /** Busy at once, before anything is read or written: the buttons are off from the first touch. */
    private fun save(guardian: Guardian) {
        step.value = GuardianStep.Sending(guardian)
        scope.launch {
            val previous = repository.current()
            try {
                repository.set(guardian)
            } catch (failure: IOException) {
                Timber.w(failure, "Relative not saved")
                step.value = GuardianStep.SaveFailed
                return@launch
            }
            if (previous != null && !sameSubscriber(previous.number, guardian.number)) farewell(previous)
            setUp(guardian)
        }
    }

    private fun setUpAgain(guardian: Guardian) {
        step.value = GuardianStep.Sending(guardian)
        scope.launch { setUp(guardian) }
    }

    private suspend fun setUp(guardian: Guardian) {
        step.value = GuardianStep.Sending(guardian)
        val sent = sender.send(guardian.number, texts.setUp())
        val ok = sent?.let { withTimeoutOrNull(SEND_WAIT) { it.await() } } == true
        step.value = if (ok) GuardianStep.Sent(guardian) else GuardianStep.NotSent(guardian)
    }

    private fun remove() {
        step.value = GuardianStep.Idle
        scope.launch {
            val previous = repository.current()
            try {
                repository.clear()
            } catch (failure: IOException) {
                Timber.w(failure, "Relative not removed")
                step.value = GuardianStep.SaveFailed
                return@launch
            }
            previous?.let(::farewell)
            step.value = GuardianStep.Removed
        }
    }

    private fun farewell(previous: Guardian) {
        if (sender.send(previous.number, texts.removed()) == null) Timber.w("Farewell text not handed to the phone")
    }

    private companion object {
        /** Seconds on a phone with a network (measured: see D-034); longer, the screen says the text did not go. */
        val SEND_WAIT = 60.seconds
    }
}
