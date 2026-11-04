package com.filestech.callhalt.platform.access

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.filestech.callhalt.platform.notification.NotificationListener
import com.filestech.callhalt.platform.stop.StopNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What the phone grants the app, read live: the user can take any access back at any time, in the phone's settings. */
interface AccessChecker {
    fun granted(): Set<Access>

    /**
     * The accesses this phone has, in [Access]'s order: [Access.AUTOSTART] on a Xiaomi only; no [Access.SCREEN] on a
     * phone that grants it to no app ([PhoneSupport.ALERT_ONLY]) — asking would send the user to a page with no switch,
     * again and again (general review of 2026-11-02, 6).
     */
    fun applicable(): List<Access>

    /** See [mayBeRestricted]. */
    fun mayBeRestricted(access: Access): Boolean

    /** How far this phone can run the protection: an "Android Go" one (low memory) before Android 14, less — see [phoneSupport]. */
    fun support(): PhoneSupport
}

@Singleton
class AndroidAccessChecker @Inject constructor(@ApplicationContext private val context: Context) : AccessChecker {

    /** Fixed for an install: read once. */
    private val packageSource: Int? by lazy(::readPackageSource)

    /** Fixed for a phone: read once. */
    private val support: PhoneSupport by lazy {
        phoneSupport(
            lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true,
            sdk = Build.VERSION.SDK_INT,
        )
    }

    override fun granted(): Set<Access> = Access.entries.filterTo(mutableSetOf(), ::isGranted)

    override fun applicable(): List<Access> {
        val xiaomi = Miui.autostartAllowed(context) != null
        return Access.entries.filter { access ->
            when (access) {
                Access.AUTOSTART -> xiaomi
                Access.SCREEN -> support != PhoneSupport.ALERT_ONLY
                else -> true
            }
        }
    }

    override fun mayBeRestricted(access: Access): Boolean = mayBeRestricted(access, Build.VERSION.SDK_INT, packageSource)

    override fun support(): PhoneSupport = support

    private fun isGranted(access: Access): Boolean = when (access) {
        Access.CALLS -> CALL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        Access.SCREEN -> Settings.canDrawOverlays(context)
        // "Unrestricted" in the app's battery page: the same list the phone's dialog writes to (measured on the S24 FE).
        Access.BACKGROUND -> context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        Access.AUTOSTART -> Miui.autostartAllowed(context) == true
        // The component, not the package: the test version has a second listener, for measurements.
        Access.NOTIFICATIONS -> context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(ComponentName(context, NotificationListener::class.java)) == true
        // Shown, and able to open the screen with the phone against the cheek: the case the fallback is most for.
        Access.ALERTS -> StopNotifier.canPost(context) && StopNotifier.canOpenFullScreen(context)
    }

    private fun readPackageSource(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        } catch (missing: PackageManager.NameNotFoundException) {
            // Its own package: cannot happen, and an unknown source keeps the help (mayBeRestricted).
            Timber.w(missing, "Install source not readable")
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
        }
    }
}
