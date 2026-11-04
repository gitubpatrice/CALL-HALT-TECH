package com.filestech.callhalt.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.filestech.callhalt.di.PhoneStateStore
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.port.Restart
import com.filestech.callhalt.domain.port.RestartSource
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The restarts the engine has not been told of ([RestartSource]): the current boot unless its count is the one last
 * recorded, with the phone's last call state, across process deaths. In doubt — a count never recorded (a first
 * install, an update), the memory failing — it is reported: reporting it changes nothing that should not change. Only a
 * boot Android does not count is not reported. No failure here costs the signal.
 *
 * Placed when this process started (P-13), the latest the restart can be — not at the uptime's zero, which a restart
 * of Android alone leaves hours back, where the after-call window of the call it cut would already be over.
 */
// Catch-alls, by design: whatever fails, the signal that asked must still be handled.
@Suppress("TooGenericExceptionCaught")
@Singleton
class BootCountRestarts @Inject constructor(
    @PhoneStateStore private val store: DataStore<Preferences>,
    private val clock: PhoneClock,
) : RestartSource {

    override suspend fun unreported(): Restart? {
        val started = processStarted() ?: return null
        return started.uptime?.takeIf { it.boot != lastRecorded() }?.let { uptime ->
            Timber.d("Phone restarted by %s", started.wall)
            Restart(started) { record(uptime.boot) }
        }
    }

    private fun processStarted(): Moment? = try {
        clock.processStarted()
    } catch (failure: Exception) {
        Timber.w(failure, "Phone clock unreadable")
        null
    }

    /** Null when never recorded, or unreadable: the boot is then reported. */
    private suspend fun lastRecorded(): Int? = try {
        store.data.first()[LAST_BOOT_COUNT]
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Timber.w(failure, "Boot memory unreadable")
        null
    }

    /** Not recorded (the memory failing): reported again at the next signal, harmlessly. */
    private suspend fun record(count: Int) {
        try {
            store.edit { preferences -> preferences[LAST_BOOT_COUNT] = count }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.w(failure, "Boot memory unwritable")
        }
    }

    private companion object {
        val LAST_BOOT_COUNT = intPreferencesKey("last_boot_count")
    }
}
