package com.filestech.callhalt.platform

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.Uptime
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The phone's clocks, read together: the time every signal carries, taken at reception (Signal's contract,
 * docs/ARCHITECTURE.md, P-13). Nothing else in the app reads the time for the engine.
 */
interface PhoneClock {
    fun now(): Moment

    /** When this process started, on the same clocks: after the last restart of the phone, for sure. */
    fun processStarted(): Moment
}

/**
 * Android's: the civil time; the boot count (`Settings.Global.BOOT_COUNT`, API 24+, readable without a permission) with
 * `elapsedRealtime`, which runs through deep sleep and which no one can set. The count is read once per process: a
 * process never outlives its boot, and the system raises the count before any app can start (`PowerManagerService`,
 * at `PHASE_THIRD_PARTY_APPS_CAN_START`, read in Android 16's sources) — so it is never the previous boot's. The same
 * phase runs again when Android alone restarts, while `elapsedRealtime` goes on from the phone's own start. Without
 * the count, the civil time alone, as before.
 */
class AndroidPhoneClock @Inject constructor(@ApplicationContext private val context: Context) : PhoneClock {

    /** Kept once read; a read that failed is tried again at the next signal, not kept for the process. */
    @Volatile private var boot: Int? = null

    override fun now(): Moment {
        val sinceBoot = Duration.ofMillis(SystemClock.elapsedRealtime())
        val count = boot ?: readBootCount()?.also { boot = it }
        return Moment(Instant.now(), count?.let { Uptime(it, sinceBoot) })
    }

    override fun processStarted(): Moment {
        val running = Duration.ofMillis(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
        return now().minus(running)
    }

    // A catch-all, by design: a settings provider that fails must cost the uptime, never the signal.
    @Suppress("TooGenericExceptionCaught")
    private fun readBootCount(): Int? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN).takeIf { it != UNKNOWN }
    } catch (failure: Exception) {
        Timber.w(failure, "Boot count unreadable")
        null
    }

    private companion object {
        const val UNKNOWN = -1
    }
}
