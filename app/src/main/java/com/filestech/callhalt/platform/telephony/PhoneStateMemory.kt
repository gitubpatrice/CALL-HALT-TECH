package com.filestech.callhalt.platform.telephony

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filestech.callhalt.di.PhoneStateStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The last call state the phone reported, kept across process deaths: Android may kill the app between the
 * ringing and the answer. It only serves to tell an answered call from a dialled one ([callSignal]).
 */
@Singleton
class PhoneStateMemory @Inject constructor(@PhoneStateStore private val store: DataStore<Preferences>) {

    /**
     * Records [current] and returns the state it replaces, in one atomic step. Null when unknown: nothing recorded
     * yet, or storage failing in any way — then the call is still reported, with its direction unknown.
     */
    // A catch-all, by design: the memory only tells a direction, and no failure of it may cost the call itself.
    @Suppress("TooGenericExceptionCaught")
    suspend fun swap(current: PhoneState): PhoneState? = try {
        var previous: PhoneState? = null
        store.edit { preferences ->
            previous = preferences[LAST_STATE]?.let { name -> PhoneState.entries.firstOrNull { it.name == name } }
            preferences[LAST_STATE] = current.name
        }
        previous
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Timber.w(failure, "Phone state memory unavailable")
        null
    }

    private companion object {
        val LAST_STATE = stringPreferencesKey("last_state")
    }
}
