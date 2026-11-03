package com.filestech.callhalt.platform.access

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import timber.log.Timber

/**
 * Xiaomi's own "autostart" (MIUI, docs/ARCHITECTURE.md, D-025, P-21). Refused by default for an app installed from a file
 * (measured on a Redmi 9C, MIUI 12, 2026-11-03): once the app is stopped, MIUI refuses to bind its notification listener
 * again — the protection deaf, the access still granted. Not an Android setting: MIUI keeps it as an app-op of its own,
 * which Android's API does not name.
 */
internal object Miui {

    /** MIUI's autostart app-op, `MIUIOP(10008)` in `appops get` (measured: "ignore" refused, "allow" allowed). */
    private const val OP_AUTO_START = 10008

    /** The list where the user allows it: "Démarrage automatique en arrière-plan", in MIUI's Security app. */
    val autostartPage: Intent = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )

    /**
     * Whether MIUI lets the app start again by itself; null on a phone without it, or where it cannot be read — then the
     * activation does not ask for it, and the home screen still sees the listener unbound (ListenerConnection). Read
     * through Android's own app-op check, by number: the method is not public (an "unsupported" one, still allowed to
     * apps), and MIUI answers it for its own ops.
     */
    @SuppressLint("DiscouragedPrivateApi") // No public way to read MIUI's own app-op; null if Android refuses it.
    fun autostartAllowed(context: Context): Boolean? {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        if (appOps == null || !hasAutostartPage(context)) return null
        return try {
            val check = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            check.invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        } catch (unreadable: ReflectiveOperationException) {
            Timber.w(unreadable, "MIUI autostart not readable")
            null
        }
    }

    /** MIUI's own page, served by a system app: a phone without it is not one where this applies. */
    @Suppress("DEPRECATION") // The Int flags: ResolveInfoFlags only exists from Android 13; they mean the same.
    private fun hasAutostartPage(context: Context): Boolean =
        context.packageManager.queryIntentActivities(autostartPage, PackageManager.MATCH_SYSTEM_ONLY).isNotEmpty()
}
