package com.filestech.callhalt.platform.access

import android.Manifest
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * What the protection needs from the phone, in the order the activation asks for it (docs/ARCHITECTURE.md, D-022,
 * D-023). Each one is of use only with the ones before it: a STOP needs a call to be seen, and a bank's request needs a
 * STOP that can show.
 */
enum class Access {
    /** When a call starts and ends, and "Hang up" ([CALL_PERMISSIONS]). */
    CALLS,

    /** The STOP screen started from the background, in front of the banking app (SYSTEM_ALERT_WINDOW, D-006). */
    SCREEN,

    /**
     * Never put to sleep: the app is exempt from the phone's battery optimisation. A protection app is hardly ever
     * opened, and phones put the apps nobody opens to sleep — on Samsung, the deep sleep kills the app and takes the
     * notification access back (measured on the S24 FE, 2026-11-03, D-023). Exempt, it is in none of Samsung's sleep
     * lists, and leaves the deep sleep if it was in it. Before [NOTIFICATIONS]: in deep sleep, the phone turns that
     * access back off until the app is out of it.
     */
    BACKGROUND,

    /** The bank's requests and the codes received by SMS, seen in their notifications (D-019, D-021). */
    NOTIFICATIONS,
}

/**
 * [Access.CALLS]: the phone group, so ONE dialog for both. Asked together because "Hang up" is what makes the STOP
 * screen act during a call, and a second question for it would be a step of nothing but doubt.
 */
val CALL_PERMISSIONS = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)

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
    if (packageSource == null || packageSource == PackageInstaller.PACKAGE_SOURCE_STORE) return false
    return when (access) {
        Access.CALLS, Access.BACKGROUND -> false
        Access.SCREEN -> sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        Access.NOTIFICATIONS -> sdk >= Build.VERSION_CODES.TIRAMISU
    }
}
