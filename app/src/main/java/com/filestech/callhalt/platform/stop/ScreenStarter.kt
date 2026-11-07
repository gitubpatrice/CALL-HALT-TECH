package com.filestech.callhalt.platform.stop

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What the launcher asks of Android, apart, so that its rules are tested on the JVM (coherence audit of 2026-11-07, C2). */
interface ScreenStarter {
    /** The app may start a screen from the background: "display over other apps" (D-006). */
    fun mayStartFromBackground(): Boolean

    /** Asks Android for [StopActivity]; false when it refused at once. A start Android blocks silently says true. */
    fun start(request: StopRequest): Boolean
}

@Singleton
class ActivityScreenStarter @Inject constructor(@ApplicationContext private val context: Context) : ScreenStarter {

    override fun mayStartFromBackground(): Boolean = Settings.canDrawOverlays(context)

    /**
     * False when the start throws, whatever it throws: the notification then takes over. Only the screen's absence was
     * caught, so a refusal of another kind (a SecurityException, a state the system rejects) left before the notification
     * and the text to the relative — no screen, no notification, no text (defect-motif audit of 2026-11-07, 4).
     */
    // A catch-all, by design: see above. Not suspending, no cancellation to let through.
    @Suppress("TooGenericExceptionCaught")
    override fun start(request: StopRequest): Boolean = try {
        context.startActivity(request.writeTo(Intent(context, StopActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (failure: RuntimeException) {
        Timber.w(failure, "STOP screen not started")
        false
    }
}
