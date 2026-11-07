package com.filestech.callhalt.platform.access

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * What the protection needs from the phone, in the order the activation asks for it (docs/ARCHITECTURE.md, D-022,
 * D-023, D-024, D-025). Each one is of use only with the ones before it: a STOP needs a call to be seen, and a bank's request needs a
 * STOP that can show.
 */
enum class Access {
    /** When a call starts and ends, and "Hang up" ([CALL_PERMISSIONS]). */
    CALLS,

    /**
     * The STOP screen started from the background, in front of the banking app (SYSTEM_ALERT_WINDOW, D-006). Not on a
     * phone that grants it to no app ([PhoneSupport.ALERT_ONLY]): the STOP comes by [ALERTS] there.
     */
    SCREEN,

    /**
     * Never put to sleep: the app is exempt from the phone's battery optimisation. A protection app is hardly ever
     * opened, and phones put the apps nobody opens to sleep — on Samsung, the deep sleep kills the app and takes the
     * notification access back (measured on the S24 FE, 2026-11-03, D-023). Exempt, it is in none of Samsung's sleep
     * lists, and leaves the deep sleep if it was in it. Before [NOTIFICATIONS]: in deep sleep, the phone turns that
     * access back off until the app is out of it.
     */
    BACKGROUND,

    /**
     * Xiaomi only: MIUI's autostart ([Miui]). Refused, once MIUI stops the app it keeps the notification listener
     * unbound — the protection deaf (measured on a Redmi 9C, D-025). After [BACKGROUND], as it: never stopped for good.
     * On any other phone it does not apply ([AccessChecker.applies]).
     */
    AUTOSTART,

    /** The bank's requests and the codes received by SMS, seen in their notifications (D-019, D-021). */
    NOTIFICATIONS,

    /**
     * The app's own notifications: the STOP screen's fallback, when the phone blocks the screen (platform/stop/StopNotifier,
     * docs/ARCHITECTURE.md, D-024). Last: of use only once a STOP can be decided. Before Android 13 the phone does not ask
     * for it — granted unless the user turned the app's notifications off.
     */
    ALERTS,
}

/**
 * [Access.CALLS]: the phone group, so ONE dialog for both. Asked together because "Hang up" is what makes the STOP
 * screen act during a call, and a second question for it would be a step of nothing but doubt.
 */
val CALL_PERMISSIONS = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)

/**
 * The runtime permissions the phone asks [access] with, in a dialog of its own; null for an access granted in the
 * phone's settings. [Access.ALERTS] is one only from Android 13.
 */
// Lint cannot read [sdk] as the version: POST_NOTIFICATIONS is a string, copied in at build, and given only from 13.
@SuppressLint("InlinedApi")
fun runtimePermissions(access: Access, sdk: Int = Build.VERSION.SDK_INT): Array<String>? = when (access) {
    Access.CALLS -> CALL_PERMISSIONS
    Access.ALERTS -> if (sdk >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else null
    Access.SCREEN, Access.BACKGROUND, Access.AUTOSTART, Access.NOTIFICATIONS -> null
}

/** How far this phone can run the protection ([phoneSupport]). */
enum class PhoneSupport {
    /** The STOP screen in front of the other apps, and its notification when the phone blocks it. */
    FULL,

    /**
     * No app may show in front of the others: the STOP comes by its notification alone (D-024) — on a phone asleep or
     * locked, the screen it opens is there when the phone is woken; on a phone in use, a touch away (docs/ARCHITECTURE.md,
     * D-030, measured).
     */
    ALERT_ONLY,

    /** Nothing the app watches can reach it. */
    NONE,
}

/**
 * What an "Android Go" phone (low memory, [lowRam]) allows — read in AOSP on 2026-11-04, measured (docs/ARCHITECTURE.md,
 * P-21, D-030); any other phone, [PhoneSupport.FULL]:
 * - Android 10: [PhoneSupport.NONE] — the system refuses every notification listener
 *   (`NotificationManagerService.canUseManagedServices`, unless the manufacturer allows the app by name): neither the
 *   bank's requests nor the codes can be seen;
 * - Android 11 to 13: [PhoneSupport.ALERT_ONLY] — listeners allowed, but the overlay list the app opens says "not
 *   available" (`ManageApplications`, `Utils.isSystemAlertWindowEnabled`). Measured on the Android 11 emulator in
 *   low-memory mode: the notification access granted in Settings and bound, the overlay page "Feature not available",
 *   the STOP screen blocked from the background, its notification shown at the top of the screen, a touch opens it;
 * - Android 14 on: [PhoneSupport.FULL] — Settings sends that list to its new page (`ManageApplications.onAttach` → SPA,
 *   on by default), which does not check the memory. Read for 14 and 15; measured on the Android 16 emulator in
 *   low-memory mode: the switch there, granted, and the STOP screen started from the background during a call. A
 *   manufacturer turning that page off is not ruled out (P-21).
 */
internal fun phoneSupport(lowRam: Boolean, sdk: Int): PhoneSupport = when {
    !lowRam || sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> PhoneSupport.FULL
    sdk >= Build.VERSION_CODES.R -> PhoneSupport.ALERT_ONLY
    else -> PhoneSupport.NONE
}

/**
 * Whether the phone may refuse [access] behind its "restricted settings", until the user allows them in the app info —
 * measured on 2026-11-02 (docs/ARCHITECTURE.md, P-3), for an APK installed from a file: notification access from
 * Android 13 (Android 14 emulator, as documented), "display over other apps" too on Android 16, not on Android 14.
 * Android 15 is not measured: assumed, so that the help shows where it may be needed.
 *
 * [packageSource] is [android.content.pm.InstallSourceInfo.getPackageSource], null before Android 13. Only an install
 * by a store is known to escape (the restriction targets files); any other source keeps the help, which costs a few
 * lines where it is not needed — where it is, its absence would leave the user stuck in front of a refusal.
 */
internal fun mayBeRestricted(access: Access, sdk: Int, packageSource: Int?): Boolean {
    if (!fromFile(packageSource)) return false
    return when (access) {
        Access.CALLS, Access.BACKGROUND, Access.AUTOSTART, Access.ALERTS -> false
        Access.SCREEN -> sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        Access.NOTIFICATIONS -> sdk >= Build.VERSION_CODES.TIRAMISU
    }
}

/**
 * Whether the phone may refuse the permission to send texts to the relative behind its "restricted settings" (D-034) —
 * measured on 2026-11-07 for an APK installed from a file: Android 15 and 16 refuse it ("App was denied access to
 * SMS") until the restricted settings are allowed in the app info; Android 14 asks as for any permission. Allowed once,
 * for the notification access during the activation, they are allowed for this too.
 */
internal fun textsMayBeRestricted(sdk: Int, packageSource: Int?): Boolean =
    fromFile(packageSource) && sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM

/** An install whose source may be restricted: known (Android 13+), and not by a store. */
private fun fromFile(packageSource: Int?): Boolean =
    packageSource != null && packageSource != PackageInstaller.PACKAGE_SOURCE_STORE
