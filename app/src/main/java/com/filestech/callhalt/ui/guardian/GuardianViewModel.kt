package com.filestech.callhalt.ui.guardian

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.callhalt.data.guardian.GuardianRepository
import com.filestech.callhalt.di.ApplicationScope
import com.filestech.callhalt.platform.access.AccessChecker
import com.filestech.callhalt.platform.guardian.ChoiceTexts
import com.filestech.callhalt.platform.guardian.ContactReader
import com.filestech.callhalt.platform.guardian.TextSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [GuardianChoice], held across a rotation; also the home screen's summary of the relative. */
@HiltViewModel
class GuardianViewModel @Inject constructor(
    repository: GuardianRepository,
    sender: TextSender,
    texts: ChoiceTexts,
    accesses: AccessChecker,
    private val contacts: ContactReader,
    @ApplicationScope work: CoroutineScope,
) : ViewModel() {

    /** Its saves and texts in the application's scope: leaving the screen does not cut them off half-way. */
    private val choice = GuardianChoice(work, repository, sender, texts)

    /** Fixed for an install: a refusal then comes with the way through the restricted settings. */
    val textsMayBeRestricted: Boolean = accesses.textsMayBeRestricted()

    val state: StateFlow<GuardianState> =
        choice.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), choice.initial())

    fun on(move: GuardianMove) = choice.on(move)

    /** The contacts app's answer: read off the main thread, then a move like any other. */
    fun picked(contact: Uri?) {
        contact ?: return
        viewModelScope.launch { choice.on(GuardianMove.Picked(contacts.read(contact))) }
    }

    private companion object {
        /** A rotation keeps the subscription. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
