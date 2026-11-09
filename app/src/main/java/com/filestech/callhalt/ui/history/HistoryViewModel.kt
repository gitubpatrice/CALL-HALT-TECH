package com.filestech.callhalt.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.callhalt.data.store.RoomJournal
import com.filestech.callhalt.domain.history.Alert
import com.filestech.callhalt.domain.history.alerts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The alerts of the journal, the newest first (D-041); null until it is read. */
@HiltViewModel
class HistoryViewModel @Inject constructor(journal: RoomJournal) : ViewModel() {

    val alerts: StateFlow<List<Alert>?> = journal.entries
        .map(::alerts)
        // Up to 10 000 lines, grouped again at each new one: off the main thread (review of 2026-11-09, 6).
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        /** Kept through a rotation, as the other screens' state. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
