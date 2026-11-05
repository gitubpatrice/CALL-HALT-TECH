package com.filestech.callhalt.platform.warning

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.WarningReason
import com.filestech.callhalt.platform.access.systemSettings
import com.filestech.callhalt.platform.install.REMOTE_CONTROL_APPS
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Carries out the engine's warnings ([Decision.ShowWarning]): an interface, so that the executor is tested on the JVM. */
fun interface WarningPoster {
    /** False when nothing was posted: a warning is told when it can be, and the journal keeps it either way. */
    fun show(warning: Decision.ShowWarning): Boolean
}

/**
 * The engine's warnings, as notifications: a risk worth telling, with no call to interrupt (docs/ARCHITECTURE.md,
 * D-031). Today a remote-control app installed outside a call (Patrice, 2026-11-05): the warning names it — by the
 * name of [REMOTE_CONTROL_APPS], never the one the app gives itself — and a touch opens its page in the phone's own
 * settings, where it is uninstalled. One per app. On a channel of its own: silencing it leaves the STOP's fallback
 * untouched.
 */
@Singleton
class WarningNotifier @Inject constructor(@ApplicationContext private val context: Context) : WarningPoster {

    private val manager = NotificationManagerCompat.from(context)

    /** At the app's start, like the STOP's channel: the user finds it by its name in the app's notification settings. */
    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.warning_channel_name))
                .setDescription(context.getString(R.string.warning_channel_description))
                .build(),
        )
    }

    override fun show(warning: Decision.ShowWarning): Boolean = when (warning.reason) {
        WarningReason.REMOTE_CONTROL_INSTALLED -> remoteControlInstalled(warning.source)
        // Not decided by the engine yet: the number of a call is not read (docs/ARCHITECTURE.md, P-8).
        WarningReason.LONG_CALL_WITH_UNKNOWN -> false
    }

    private fun remoteControlInstalled(packageName: String?): Boolean {
        val name = packageName?.let(REMOTE_CONTROL_APPS::get)
        return when {
            packageName == null || name == null -> {
                // Only the install sensor asks for this warning, with an app of the list: anything else names nothing.
                Timber.w("Remote-control warning for an app not in the list: %s", packageName)
                false
            }
            !canPost() -> false
            else -> post(packageName, name)
        }
    }

    @SuppressLint("MissingPermission") // canPost read POST_NOTIFICATIONS first.
    private fun post(packageName: String, name: String): Boolean {
        val text = context.getString(R.string.warning_remote_installed_text)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stop_notification)
            .setContentTitle(context.getString(R.string.warning_remote_installed_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Nothing private in it: which app was installed, and what to do.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(appPage(packageName))
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(packageName, NOTIFICATION_ID, notification)
            true
        } catch (refused: SecurityException) {
            Timber.w(refused, "Warning notification refused")
            false
        }
    }

    /**
     * The installed app's page in the phone's OWN settings, where "Uninstall" is — never an app that declares the same
     * action ([systemSettings]); else the phone's settings; else nothing to open, the text says what to do.
     */
    private fun appPage(packageName: String): PendingIntent? {
        val page = systemSettings(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        ) ?: systemSettings(context, Intent(Settings.ACTION_SETTINGS)) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The permission (Android 13+), the app's notifications on, and this channel not turned off by the user. */
    private fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channel = manager.getNotificationChannelCompat(CHANNEL)
        return permitted && manager.areNotificationsEnabled() &&
            channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    private companion object {
        const val CHANNEL = "warnings"

        /** One per app: the tag is the package name. Not the STOP's (1), on a channel of its own anyway. */
        const val NOTIFICATION_ID = 2
    }
}
