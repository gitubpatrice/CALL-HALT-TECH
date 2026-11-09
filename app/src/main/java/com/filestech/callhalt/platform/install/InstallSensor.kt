package com.filestech.callhalt.platform.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.queueAsync
import com.filestech.callhalt.platform.warning.WarningNotifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hears a remote-control app being installed ([REMOTE_CONTROL_APPS], docs/ARCHITECTURE.md, D-031): during a call or
 * just after it, a STOP that offers to uninstall it, and its warning, silent, for after the call (FN-3, D-039); outside,
 * the warning. And being uninstalled: its warning goes — it would otherwise say the app was just installed, and open
 * the page of an app no longer there.
 *
 * ACTION_PACKAGE_ADDED reaches no receiver declared in the manifest (the ban on implicit broadcasts, Android 8+), only
 * one registered at run time: by the application, for the life of its process, which the notification listener keeps
 * running (D-023). Of the other apps' installations, Android tells it only those it may see — the apps of the list,
 * declared in the manifest's `<queries>`. Not exported: protected broadcasts, sent by the system alone.
 */
@Singleton
class InstallSensor @Inject constructor(
    private val queue: SensorQueue,
    private val clock: PhoneClock,
    /** Resolved in the queue, never at injection time: it reaches the database (see [queueAsync]). */
    private val processSignal: Provider<ProcessSignalUseCase>,
    private val warnings: WarningNotifier,
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            // An update removes the app, then adds it back: neither is an installation, nor an uninstallation.
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> installed(packageName, replacing)
                Intent.ACTION_PACKAGE_REMOVED -> if (withdrawsWarning(packageName, replacing)) warnings.withdraw(packageName)
            }
        }

        private fun installed(packageName: String, replacing: Boolean) {
            // The phone's clocks at reception: the time every signal carries (Signal's contract).
            val signal = installSignal(packageName, replacing, clock.now()) ?: return
            Timber.d("Remote-control app installed: %s", signal.source)
            queueAsync(queue, reason = "remote-control app installed") { processSignal.get()(signal) }
        }
    }

    /** Once per process, from the application. */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
