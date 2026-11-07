package com.filestech.callhalt.platform.stop

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.ui.stop.stopText
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The STOP screen's fallback (docs/ARCHITECTURE.md, D-024, P-19): when the phone blocks the screen started from the
 * background, a notification that opens it. On a locked or sleeping phone it opens the screen at once (a full-screen
 * notification); otherwise it shows at the top of the screen, and a touch opens it — against the cheek, once the screen
 * is back on. One at a time: a later STOP takes its place, and any STOP screen shown removes it.
 */
@Singleton
class StopNotifier @Inject constructor(@ApplicationContext private val context: Context) : StopNotice {

    private val manager = NotificationManagerCompat.from(context)
    private val main = Handler(Looper.getMainLooper())

    /** Waiting for the screen to come back on ([alertAgainWhenScreenOn]); touched on the main thread only. */
    private var waiting: DisplayManager.DisplayListener? = null

    /**
     * The triggers the posted notification stands for, in their order of arrival: a later STOP replaces it, and must not
     * lose them (publication audit, N-1). Guarded by this object's lock: posted from the sensor queue, cancelled by the
     * screen on the main thread.
     */
    private var absorbed: List<SensitiveActionKind> = emptyList()

    /** The id of the request the posted notification opens; same lock. */
    private var postedId: Long? = null

    /** The triggers of the STOP notification still waiting to be touched, oldest first; none once it is gone. */
    @Synchronized
    override fun pending(): List<SensitiveActionKind> = if (absorbed.isNotEmpty() && stillPosted()) absorbed else emptyList()

    /** At the app's start: the user then finds it by its name in the app's notification settings. Idempotent. */
    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.stop_channel_name))
                .setDescription(context.getString(R.string.stop_channel_description))
                .build(),
        )
    }

    /**
     * False when the phone would show nothing: [canPost] false, or a refusal the check could not foresee. Replacing one
     * still waiting, it stands for that one's triggers too, and speaks of the gravest.
     */
    @SuppressLint("MissingPermission") // canPost reads POST_NOTIFICATIONS first.
    @Synchronized
    override fun post(request: StopRequest): Boolean {
        if (!canPost(context)) return false
        val triggers = inArrivalOrder(pending() + request.earlier + request.trigger)
        val open = PendingIntent.getActivity(
            context,
            0,
            request.copy(earlier = triggers - request.trigger).writeTo(Intent(context, StopActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shown = gravest(triggers) ?: request.trigger
        val happened = context.getString(stopText(shown, request.onCall, request.channel, canEndCall = false).happened)
        val text = context.getString(R.string.stop_notification_text, happened)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stop_notification)
            .setContentTitle(context.getString(R.string.stop_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Nothing private in it: on the lock screen, where the phone against the cheek wakes up.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            // Swiped away without being opened: the STOP left without an answer (FN-4).
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    0,
                    StopNotificationSwiped.intent(context, triggers),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            absorbed = triggers
            postedId = request.id
            alertAgainWhenScreenOn(notification)
            true
        } catch (refused: SecurityException) {
            Timber.w(refused, "STOP notification refused")
            false
        }
    }

    /**
     * A STOP screen got through, for request [shownId]: the notification has nothing left to open — the screen stands for
     * its triggers. Unless it opens a LATER request (ids only grow, StopDisplay): a STOP posted while the screen of an
     * earlier one was starting — a touch on the notification, then a new STOP before the screen was created — would be
     * lost with it (Gemini review of 2026-11-07, 5); it stays, and opens its own.
     */
    @Synchronized
    override fun cancel(shownId: Long) {
        val later = postedId?.let { it > shownId } == true
        if (later) return
        manager.cancel(NOTIFICATION_ID)
        absorbed = emptyList()
        postedId = null
        main.post(::stopWaiting)
    }

    /**
     * The phone against the cheek (measured on the S24 FE, 2026-11-03): the screen is off, by the proximity sensor, but
     * the phone neither asleep nor locked. Android then neither opens the full screen nor shows the banner, and the
     * notification waits in the shade, unseen when the user looks again. Posted again once the screen is back on, it
     * shows at the top of it. Waits until then, or until the notification is gone (touched, or a STOP screen shown).
     */
    @SuppressLint("MissingPermission") // Posted again only while it is there: the permission was read to post it.
    private fun alertAgainWhenScreenOn(notification: Notification) = main.post {
        stopWaiting()
        val displays = context.getSystemService(DisplayManager::class.java) ?: return@post
        if (displays.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON) return@post
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                if (displays.getDisplay(displayId)?.state != Display.STATE_ON) return
                stopWaiting()
                if (stillPosted()) manager.notify(NOTIFICATION_ID, notification)
                Timber.i("Screen back on: STOP notification posted again")
            }

            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        displays.registerDisplayListener(listener, main)
        waiting = listener
        Timber.i("Screen off: the STOP notification is posted again once it is back on")
    }

    /** On the main thread only, as [waiting]. */
    private fun stopWaiting() {
        waiting?.let { context.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(it) }
        waiting = null
    }

    private fun stillPosted(): Boolean = manager.activeNotifications.any { it.id == NOTIFICATION_ID }

    companion object {
        private const val CHANNEL = "stop"
        private const val NOTIFICATION_ID = 1

        /**
         * Whether the phone shows the app's notifications on the screen: the permission (Android 13+), the app's
         * notifications on, and the STOP channel still urgent. Turned down by the user (silent, or blocked), it shows
         * neither at the top of the screen nor in full screen: a STOP nobody sees (review of 2026-11-03, R-01). A
         * channel not created yet is created at the next start, urgent.
         */
        fun canPost(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            val channel = manager.getNotificationChannelCompat(CHANNEL)
            return permitted && manager.areNotificationsEnabled() &&
                (channel == null || channel.importance >= NotificationManagerCompat.IMPORTANCE_HIGH)
        }

        /**
         * Whether the notification may open the STOP screen by itself, on a locked or sleeping phone — the phone against
         * the cheek. From Android 14 a setting of its own (a switch of the app's notification settings on Android 16),
         * granted to an app installed from a file (measured) but which the user, or a maker, can turn off (review of
         * 2026-11-03, R-03). Without it the notification still shows, at the top of the screen.
         */
        fun canOpenFullScreen(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    }
}
