package com.filestech.callhalt.platform.stop

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.filestech.callhalt.R
import com.filestech.callhalt.ui.stop.stopText
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The STOP screen's fallback (docs/ARCHITECTURE.md, D-024, P-19): when the phone blocks the screen started from the
 * background, a notification that opens it. On a locked or sleeping phone it opens the screen at once (a full-screen
 * notification); otherwise it shows at the top of the screen, and a touch opens it. One at a time: a later STOP takes
 * its place, and any STOP screen shown removes it.
 */
@Singleton
class StopNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** At the app's start: the user then finds it by its name in the app's notification settings. Idempotent. */
    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.stop_channel_name))
                .setDescription(context.getString(R.string.stop_channel_description))
                .build(),
        )
    }

    /** False when the phone would show nothing: [canPost] false, or a refusal the check could not foresee. */
    @SuppressLint("MissingPermission") // canPost reads POST_NOTIFICATIONS first.
    fun post(request: StopRequest): Boolean {
        if (!canPost(context)) return false
        val open = PendingIntent.getActivity(
            context,
            0,
            request.writeTo(Intent(context, StopActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val happened = context.getString(stopText(request.trigger, request.onCall, request.channel, canEndCall = false).happened)
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
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (refused: SecurityException) {
            Timber.w(refused, "STOP notification refused")
            false
        }
    }

    /** A STOP screen got through: the notification has nothing left to open. */
    fun cancel() = manager.cancel(NOTIFICATION_ID)

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
