package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.SensitiveActionKind
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** Puts one STOP screen in front of the user, and says whether it got there. */
fun interface StopLauncher {
    suspend fun show(stop: Decision.ShowStop): Boolean
}

/** The STOP screen's fallback notification, as the launcher needs it ([StopNotifier]); apart, for the JVM tests (C2). */
interface StopNotice {
    /** The triggers of a STOP notification still waiting to be touched, oldest first; none once it is gone. */
    fun pending(): List<SensitiveActionKind>

    /** False when the phone would show nothing. */
    fun post(request: StopRequest): Boolean

    /** A STOP screen got through for request [shownId]: the notification, unless it opens a later request, goes. */
    fun cancel(shownId: Long)
}

/**
 * Starts [StopActivity] from the background — allowed to an app holding "display over other apps"
 * (SYSTEM_ALERT_WINDOW, docs/ARCHITECTURE.md, D-006) — then waits for the screen to say it is in front. An
 * activity, never an overlay window: a banking app hides overlays. Blocked by the phone (Xiaomi's "pop-up windows
 * in the background", P-21, or the access taken back), the notification that opens it ([StopNotifier], D-024):
 * the user is told, which is what "shown" means here. Without that access, the notification at once (D-030).
 */
@Singleton
class ActivityStopLauncher @Inject constructor(
    private val starter: ScreenStarter,
    private val display: StopDisplay,
    private val notice: StopNotice,
) : StopLauncher {

    override suspend fun show(stop: Decision.ShowStop): Boolean {
        // A STOP notification still waiting: the screen started now stands for its triggers too, and removes it (N-1).
        val request = StopRequest(display.nextId(), stop.trigger, stop.call.isActive, stop.call.channel, notice.pending())
        val started = starter.start(request)
        // Without the display over the others, Android blocks a screen started from the background, whatever the phone
        // (unless the app is in front, the screen then removes the notification): no wait, the notification at once — on
        // an "Android Go" phone before Android 14 it is how the STOP always comes, 5 s sooner (D-030, measured). With it,
        // the screen is waited for: the phone may still block it (Samsung, Xiaomi: D-024).
        val overlay = starter.mayStartFromBackground()
        if (overlay && started && display.awaitShown(request.id, SHOW_TIMEOUT)) return true
        if (overlay) {
            Timber.w("STOP screen not in front after %s: blocked by the system? The notification instead", SHOW_TIMEOUT)
        } else {
            Timber.w("No display over the others: the notification at once")
        }
        // No notification either: the screen alone says whether it got through — waited for here if it was not above (the
        // app in front lets it through without the display over the others). Else a STOP shown would be journaled as not
        // shown (review of 2026-11-04, D-030).
        val shown = notified(request) ||
            display.isShown(request.id) ||
            (!overlay && started && display.awaitShown(request.id, SHOW_TIMEOUT))
        if (!shown) Timber.w("STOP notification not allowed either")
        return shown
    }

    /** The notification that opens the screen ([StopNotifier]); false if the phone refuses it. */
    private fun notified(request: StopRequest): Boolean {
        val posted = notice.post(request)
        // The screen may have got through between the wait and the post: it removed nothing yet, so this does — or the
        // notification would stay, and a touch later open a STOP already answered (review of 2026-11-03, R-02). Got
        // through after this, the screen removes it itself.
        if (posted && display.isShown(request.id)) notice.cancel(request.id)
        return posted
    }

    internal companion object {
        /**
         * Long enough for a cold start of the screen on an old phone, short enough for the broadcast holding the
         * queue (8 s at most, SensorQueue).
         */
        val SHOW_TIMEOUT = 5.seconds
    }
}
