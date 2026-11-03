package com.filestech.callhalt.platform.access

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    /**
     * The phone's own dialog, "Let app always run in background?": one touch, where the app info takes three (its battery
     * page, then "Unrestricted"). Then the app info, where it is granted if the dialog was refused. Lint warns that the
     * Play Store keeps this dialog to some kinds of apps: this one is published on F-Droid (D-016), and its core function
     * stops without it (measured on the S24 FE, D-023).
     */
    @SuppressLint("BatteryLife")
    fun background(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))) + fallbacks(context)

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
            val system = systemSettings(context, page) ?: continue
            if (start(context, system)) return
        }
        // No system page found (a phone this was not measured on): the first page as it is, rather than a button that
        // does nothing.
        Timber.w("No system settings page: %s left to the phone", pages.first().action)
        start(context, pages.first())
    }

    /**
     * [page] as the phone's OWN settings serve it. Any app can declare the same action: the phone would then offer it
     * beside them, and it could imitate them and give its own instructions (security review of 2026-11-02, S2). Seen
     * through the manifest's `<queries>`. Null when no system app serves the page.
     */
    @Suppress("DEPRECATION") // The Int flags: ResolveInfoFlags only exists from Android 13; they mean the same.
    private fun systemSettings(context: Context, page: Intent): Intent? =
        context.packageManager.queryIntentActivities(page, PackageManager.MATCH_SYSTEM_ONLY).firstOrNull()
            ?.let { settings -> Intent(page).setPackage(settings.activityInfo.packageName) }

    private fun start(context: Context, page: Intent): Boolean = try {
        context.startActivity(page)
        true
    } catch (missing: ActivityNotFoundException) {
        Timber.w(missing, "No settings page for %s", page.action)
        false
    } catch (refused: SecurityException) {
        // A page some maker does not export (general review of 2026-11-02, 7): the next one, not a crash.
        Timber.w(refused, "Settings page refused: %s", page.action)
        false
    }

    private fun fallbacks(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)), Intent(Settings.ACTION_SETTINGS))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}
