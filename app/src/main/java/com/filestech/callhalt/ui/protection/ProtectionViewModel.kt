package com.filestech.callhalt.ui.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.callhalt.data.settings.SettingsRepository
import com.filestech.callhalt.data.store.RoomDetectorStore
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.platform.access.Access
import com.filestech.callhalt.platform.access.AccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the home and activation screens show (docs/ARCHITECTURE.md, D-022). */
data class ProtectionState(
    val granted: Set<Access>,
    /** The accesses the phone may refuse until the user allows the restricted settings ([AccessChecker.mayBeRestricted]). */
    val mayBeRestricted: Set<Access>,
    val afterCallMinutes: Long,
    /** [AccessChecker.phoneCanProtect]: when false, nothing is asked — the phone would refuse the STOP screen. */
    val phoneCanProtect: Boolean = true,
    /** [RoomDetectorStore.failing]: the protection still decides, but forgets the call if the phone stops the app (P-10). */
    val storageFailing: Boolean = false,
) {
    /** In the order the activation asks for them. */
    val missing: List<Access> = Access.entries.filterNot { it in granted }

    /** The first problem only, as the activation asks for one access at a time: the accesses before the storage. */
    val status: ProtectionStatus = when {
        !phoneCanProtect -> ProtectionStatus.UNSUPPORTED
        missing.isNotEmpty() -> ProtectionStatus.OFF
        storageFailing -> ProtectionStatus.WEAKENED
        else -> ProtectionStatus.ON
    }
}

/** What the home screen says of the protection. */
enum class ProtectionStatus { ON, WEAKENED, OFF, UNSUPPORTED }

/**
 * The accesses are read again by [refresh], each time a screen comes back to the front: the user grants them in the
 * phone's settings, and can take them back there at any time. Storage is tried again at the same moment.
 */
@HiltViewModel
class ProtectionViewModel @Inject constructor(
    private val accesses: AccessChecker,
    private val store: RoomDetectorStore,
    settings: SettingsRepository,
) : ViewModel() {

    private val granted = MutableStateFlow(accesses.granted())
    private val mayBeRestricted = Access.entries.filterTo(mutableSetOf(), accesses::mayBeRestricted)
    private val phoneCanProtect = accesses.phoneCanProtect()

    val state: StateFlow<ProtectionState> = combine(granted, settings.settings, store.failing) { granted, current, failing ->
        state(granted, current.afterCallWindow.toMinutes(), failing)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        state(granted.value, ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.toMinutes(), store.failing.value),
    )

    fun refresh() {
        granted.value = accesses.granted()
        viewModelScope.launch { store.check() }
    }

    private fun state(granted: Set<Access>, afterCallMinutes: Long, storageFailing: Boolean) =
        ProtectionState(granted, mayBeRestricted, afterCallMinutes, phoneCanProtect, storageFailing)

    private companion object {
        /** A rotation keeps the subscription. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
