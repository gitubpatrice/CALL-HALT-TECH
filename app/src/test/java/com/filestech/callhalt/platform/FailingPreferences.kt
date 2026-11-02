package com.filestech.callhalt.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A DataStore whose every read and write fails with [failure]: the memories built on it must not fail with it. */
class FailingPreferences(private val failure: Exception) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw failure }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw failure
}
