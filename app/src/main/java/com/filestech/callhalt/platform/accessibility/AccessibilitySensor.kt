package com.filestech.callhalt.platform.accessibility

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Sees an app being given the accessibility access (docs/ARCHITECTURE.md, D-033): during a call or just after it, a
 * STOP — the access a remote-control app needs to take the phone in hand (measured: RustDesk's "input control"), and a
 * spying app to read what is on the screen; it is also what is left when the user refused a remote-control app's
 * notifications, which D-032 then cannot see. Outside a call, nothing: the user's own choice. The phone's own features
 * are not this ([SYSTEM_ACCESSIBILITY_SUITES]).
 *
 * Android keeps the services turned on in one setting, which any app may read and watch, no permission asked: watched
 * by the application, for the life of its process, which the notification listener keeps running (D-023). What was on
 * when the process started is not new; unknown then (the setting refused), the first change is only read. Main thread
 * only: the observer's callbacks come there.
 */
@Singleton
class AccessibilitySensor @Inject constructor(
    private val queue: SensorQueue,
    private val clock: PhoneClock,
    /** Resolved in the queue, never at injection time: it reaches the database. */
    private val processSignal: Provider<ProcessSignalUseCase>,
) {

    /** Null while unknown: nothing is decided on a guess (review of 2026-11-05, 10). */
    private var known: Set<String>? = null

    /** Once per process, from the application. */
    fun register(context: Context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // The phone's clocks at reception: the time every signal carries (Signal's contract).
                val at = clock.now()
                val now = read(resolver)
                val before = known
                // Unreadable, the state is unknown again: the next read is only the base, never a stale comparison
                // (GPT review of 2026-11-05, 3).
                known = now
                if (now == null) return
                val signal = before?.let {
                    accessibilityGranted(it, now, context.packageName, at) { pkg -> isSystemSuite(context, pkg) }
                } ?: return
                Timber.d("Accessibility access given to %s", signal.source)
                queue.submit(reason = "accessibility enabled") { processSignal.get()(signal) }
            }
        }
        // Watched, then read: a change in between lands in the base, like any before the process started — microseconds
        // at a process start, which no order closes (GPT review of 2026-11-05, 1).
        resolver.registerContentObserver(Settings.Secure.getUriFor(SETTING), false, observer)
        known = read(resolver)
    }

    /** Null when the phone refuses it. */
    private fun read(resolver: ContentResolver): Set<String>? = try {
        enabledServices(Settings.Secure.getString(resolver, SETTING))
    } catch (refused: SecurityException) {
        Timber.w(refused, "Accessibility services unreadable")
        null
    }

    private companion object {
        const val SETTING = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES

        /** One of the phone's own features ([isPhoneFeature]): preinstalled, or signed by its publisher. Unseen: not one. */
        fun isSystemSuite(context: Context, packageName: String): Boolean {
            val packages = context.packageManager
            val feature = isPhoneFeature(
                packageName,
                preinstalled = { pkg ->
                    try {
                        packages.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
                    } catch (unseen: PackageManager.NameNotFoundException) {
                        Timber.w(unseen, "Accessibility suite not seen: %s", pkg)
                        false
                    }
                },
                signedWith = packages::isSignedWith,
            )
            if (feature) Timber.d("Accessibility access given to the phone's own %s: nothing", packageName)
            return feature
        }
    }
}
