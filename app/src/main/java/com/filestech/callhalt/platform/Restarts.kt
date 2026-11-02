package com.filestech.callhalt.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.filestech.callhalt.di.PhoneStateStore
import com.filestech.callhalt.domain.port.Restart
import com.filestech.callhalt.domain.port.RestartSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** One boot of the phone: how many boots Android has counted so far, and when this one happened. */
data class Boot(val count: Int, val at: Instant)

/** The phone's current boot, or null when Android does not say. */
fun interface BootReader {
    fun current(): Boot?
}

/**
 * Android counts the boots (`Settings.Global.BOOT_COUNT`, API 24+, readable without a permission). The time of this
 * one is the phone's clock now, minus the time since boot — `elapsedRealtime`, which counts deep sleep too.
 */
class AndroidBootReader @Inject constructor(@ApplicationContext private val context: Context) : BootReader {
    override fun current(): Boot? {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN)
        if (count == UNKNOWN) return null
        return Boot(count, Instant.now().minusMillis(SystemClock.elapsedRealtime()))
    }

    private companion object {
        const val UNKNOWN = -1
    }
}

/**
 * The restarts the engine has not been told of ([RestartSource]): the current boot, unless its count is the one
 * last recorded, with the phone's last call state, across process deaths. In doubt — a count never recorded (a
 * first install, an update), the memory failing — it is reported: reporting it changes nothing that should not
 * change. Only a boot Android does not give is not reported. No failure here costs the signal.
 */
// Catch-alls, by design: whatever fails, the signal that asked must still be handled.
@Suppress("TooGenericExceptionCaught")
@Singleton
class BootCountRestarts @Inject constructor(
    @PhoneStateStore private val store: DataStore<Preferences>,
    private val boots: BootReader,
) : RestartSource {

    override suspend fun unreported(): Restart? {
        val boot = currentBoot()?.takeIf { it.count != lastRecorded() } ?: return null
        Timber.d("Phone restarted at %s", boot.at)
        return Restart(boot.at) { record(boot.count) }
    }

    private fun currentBoot(): Boot? = try {
        boots.current()
    } catch (failure: Exception) {
        Timber.w(failure, "Boot count unreadable")
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
