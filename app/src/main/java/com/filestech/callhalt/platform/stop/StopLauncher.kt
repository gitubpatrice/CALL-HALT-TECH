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
 * activity, never an overlay window: a banking app hides overlays.
 */
@Singleton
class ActivityStopLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val display: StopDisplay,
) : StopLauncher {

    override suspend fun show(stop: Decision.ShowStop): Boolean {
        val request = StopRequest(display.nextId(), stop.trigger, stop.call.isActive, stop.call.channel)
        val intent = request.writeTo(Intent(context, StopActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (failure: ActivityNotFoundException) {
            Timber.w(failure, "STOP screen not started")
            return false
        }
        return display.awaitShown(request.id, SHOW_TIMEOUT).also { shown ->
            if (!shown) Timber.w("STOP screen not in front after %s: blocked by the system?", SHOW_TIMEOUT)
        }
    }

    private companion object {
        /**
         * Long enough for a cold start of the screen on an old phone, short enough for the broadcast holding the
         * queue (8 s at most, SensorQueue).
         */
        val SHOW_TIMEOUT = 5.seconds
    }
}
