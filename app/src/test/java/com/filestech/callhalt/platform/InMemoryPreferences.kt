package com.filestech.callhalt.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A DataStore that keeps its contract — one update at a time, each seeing the previous one — in memory. On the
 * Windows JVM, the real one cannot rename its temporary file over the previous file, as Android does in one step.
 */
class InMemoryPreferences : DataStore<Preferences> {
    private val current = MutableStateFlow(emptyPreferences())
    private val writes = Mutex()
    override val data: Flow<Preferences> = current

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        writes.withLock { transform(current.value).also { current.value = it } }
}
