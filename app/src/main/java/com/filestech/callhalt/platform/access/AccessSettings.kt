package com.filestech.callhalt.platform.access

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.filestech.callhalt.platform.notification.NotificationListener
import timber.log.Timber

/**
 * The phone's settings pages where the user grants an access, the most precise first: a page one phone lacks
 * (ActivityNotFoundException), the next one covers. The phone's own settings last: there, at least, everything is.
 */
object AccessSettings {

    /**
     * From Android 11 the phone ignores the package and opens the list of apps (measured on Android 14 and 16): the
     * activation says to touch the app's name there.
     */
    fun screen(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))) + fallbacks(context)

    /** From Android 11, the app's own page; before, the list of the apps that asked. */
    fun notifications(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val listener = ComponentName(context, NotificationListener::class.java).flattenToString()
            add(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener),
            )
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        addAll(fallbacks(context))
    }

    /** The app info: where a permission the phone no longer asks for is granted, and the restricted settings allowed. */
    fun appInfo(context: Context): List<Intent> = fallbacks(context)

    /**
     * Opens the first page this phone has. [context] must be the activity's: started without a new task, the page
     * goes back to the app with the Back key, and the phone's settings, if already open, cannot swallow it (measured:
     * started as a new task while they are in front, the intent reaches the page shown and nothing changes).
     */
    fun open(context: Context, pages: List<Intent>) {
        for (page in pages) {
            try {
                context.startActivity(page)
                return
            } catch (missing: ActivityNotFoundException) {
                Timber.w(missing, "No settings page for %s", page.action)
            }
        }
    }

    private fun fallbacks(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)), Intent(Settings.ACTION_SETTINGS))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}
