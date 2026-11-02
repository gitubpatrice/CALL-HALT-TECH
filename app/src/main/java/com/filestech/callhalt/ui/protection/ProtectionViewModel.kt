package com.filestech.callhalt.ui.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.callhalt.data.settings.SettingsRepository
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.platform.access.Access
import com.filestech.callhalt.platform.access.AccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the home and activation screens show (docs/ARCHITECTURE.md, D-022). */
data class ProtectionState(
    val granted: Set<Access>,
    /** The accesses the phone may refuse until the user allows the restricted settings ([AccessChecker.mayBeRestricted]). */
    val mayBeRestricted: Set<Access>,
    val afterCallMinutes: Long,
    /** [AccessChecker.phoneCanProtect]: when false, nothing is asked — the phone would refuse the STOP screen. */
    val phoneCanProtect: Boolean = true,
) {
    /** In the order the activation asks for them. */
    val missing: List<Access> = Access.entries.filterNot { it in granted }
}

/**
 * The accesses are read again by [refresh], each time a screen comes back to the front: the user grants them in the
 * phone's settings, and can take them back there at any time.
 */
@HiltViewModel
class ProtectionViewModel @Inject constructor(
    private val accesses: AccessChecker,
    settings: SettingsRepository,
) : ViewModel() {

    private val granted = MutableStateFlow(accesses.granted())
    private val mayBeRestricted = Access.entries.filterTo(mutableSetOf(), accesses::mayBeRestricted)
    private val phoneCanProtect = accesses.phoneCanProtect()

    val state: StateFlow<ProtectionState> = combine(granted, settings.settings) { granted, current ->
        state(granted, current.afterCallWindow.toMinutes())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        state(granted.value, ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.toMinutes()),
    )

    fun refresh() {
        granted.value = accesses.granted()
    }

    private fun state(granted: Set<Access>, afterCallMinutes: Long) =
        ProtectionState(granted, mayBeRestricted, afterCallMinutes, phoneCanProtect)

    private companion object {
        /** A rotation keeps the subscription. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
