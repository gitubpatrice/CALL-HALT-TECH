package com.filestech.callhalt.platform.stop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.filestech.callhalt.domain.model.Decision
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** Puts one STOP screen in front of the user, and says whether it got there. */
fun interface StopLauncher {
    suspend fun show(stop: Decision.ShowStop): Boolean
}

/**
 * Starts [StopActivity] from the background — allowed to an app holding "display over other apps"
 * (SYSTEM_ALERT_WINDOW, docs/ARCHITECTURE.md, D-006) — then waits for the screen to say it is in front. An
 * activity, never an overlay window: a banking app hides overlays. Blocked by the phone (Xiaomi's "pop-up windows
 * in the background", P-21, or the access taken back), the notification that opens it ([StopNotifier], D-024):
 * the user is told, which is what "shown" means here.
 */
@Singleton
class ActivityStopLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val display: StopDisplay,
    private val notifier: StopNotifier,
) : StopLauncher {

    override suspend fun show(stop: Decision.ShowStop): Boolean {
        val request = StopRequest(display.nextId(), stop.trigger, stop.call.isActive, stop.call.channel)
        if (started(request) && display.awaitShown(request.id, SHOW_TIMEOUT)) return true
        Timber.w("STOP screen not in front after %s: blocked by the system? The notification instead", SHOW_TIMEOUT)
        val posted = notifier.post(request)
        // The screen may have got through between the wait and the post: it removed nothing yet, so this does — or the
        // notification would stay, and a touch later open a STOP already answered (review of 2026-11-03, R-02). Got
        // through after this, the screen removes it itself.
        if (posted && display.isShown(request.id)) notifier.cancel()
        if (!posted) Timber.w("STOP notification not allowed either")
        return posted
    }

    private fun started(request: StopRequest): Boolean = try {
        context.startActivity(request.writeTo(Intent(context, StopActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (failure: ActivityNotFoundException) {
        Timber.w(failure, "STOP screen not started")
        false
    }

    private companion object {
        /**
         * Long enough for a cold start of the screen on an old phone, short enough for the broadcast holding the
         * queue (8 s at most, SensorQueue).
         */
        val SHOW_TIMEOUT = 5.seconds
    }
}
