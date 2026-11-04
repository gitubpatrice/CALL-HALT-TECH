package com.filestech.callhalt.platform.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.filestech.callhalt.domain.model.PhoneShuttingDown
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.queueAsync
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hears the phone switching off or restarting cleanly — the power menu, a battery gone critical, an update — and ends
 * the calls in progress there ([PhoneShuttingDown], docs/ARCHITECTURE.md, D-028).
 *
 * ACTION_SHUTDOWN reaches the receivers registered at run time only (`FLAG_RECEIVER_REGISTERED_ONLY`, AOSP
 * `ShutdownThread`, Android 10 to 16): registered by the application, for the life of its process, which the
 * notification listener keeps running (D-023). Android waits up to 10 s for it: one transaction, on the disk before it
 * returns (D-027). Not exported: a protected broadcast, sent by the system alone, which a non-exported receiver hears
 * (measured on Android 10 and 16). A process not running then, or frozen (the broadcast waits until it is active, and
 * Android does not wait more than 10 s), or a power cut: nothing is heard, and the restart places the end, as before
 * (D-020). Behind a long task in the queue, the broadcast is released after `BROADCAST_HOLD_LIMIT` (8 s) all the same.
 */
@Singleton
class ShutdownSensor @Inject constructor(
    private val queue: SensorQueue,
    private val clock: PhoneClock,
    /** Resolved in the queue, never at injection time: it reaches the database (see [queueAsync]). */
    private val processSignal: Provider<ProcessSignalUseCase>,
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SHUTDOWN) return
            // The phone's clocks at reception: the time every signal carries (Signal's contract).
            val at = clock.now()
            Timber.d("Phone shutting down")
            queueAsync(queue, reason = "shutdown") { processSignal.get()(PhoneShuttingDown(at)) }
        }
    }

    /** Once per process, from the application. */
    fun register(context: Context) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
