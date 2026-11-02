package com.filestech.callhalt.platform.debug

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber

/**
 * DEBUG BUILDS ONLY (src/debug): the P-2 measurement (docs/ARCHITECTURE.md, § 7) — can the app read a banking app's
 * request to approve an operation? It logs what Android hands a notification listener, for the MEASURED packages
 * only, to the phone's own log (logcat, on the developer's computer). Nothing is stored, nothing is decided: the
 * real sensor is designed from what this shows.
 *
 * Enabled from adb, no screen:
 *
 *     adb shell cmd notification allow_listener \
 *         com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.MeasureNotificationListener
 */
class MeasureNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Timber.i("P-2 listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MEASURED) return
        val notification = sbn.notification
        val extras = notification.extras
        Timber.i(
            "P-2 posted: package=%s id=%d tag=%s category=%s channel=%s ongoing=%s flags=%d visibility=%d",
            sbn.packageName,
            sbn.id,
            sbn.tag,
            notification.category,
            notification.channelId,
            sbn.isOngoing,
            notification.flags,
            notification.visibility,
        )
        Timber.i(
            "P-2 content: title=[%s] text=[%s] big=[%s] sub=[%s] info=[%s]",
            masked(extras.getCharSequence(Notification.EXTRA_TITLE)),
            masked(extras.getCharSequence(Notification.EXTRA_TEXT)),
            masked(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
            masked(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
            masked(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)),
        )
        Timber.i(
            "P-2 shape: extras=%s actions=%s fullScreen=%s",
            extras.keySet().sorted(),
            notification.actions?.map { it.title },
            notification.fullScreenIntent != null,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName !in MEASURED) return
        // REASON_CLICK (1): the user tapped it — idea 15 of PROJET.md § 9.2, "the press on Validate".
        Timber.i("P-2 removed: package=%s id=%d reason=%d", sbn.packageName, sbn.id, reason)
    }

    /**
     * The words, which the sensor will be designed from, without the digits: no amount, IBAN or code in the log
     * (security review of 2026-11-02). A redacted text (Android 15+ hides codes from listeners) reads as it is.
     */
    private fun masked(text: CharSequence?): String? = text?.toString()?.replace(DIGIT, "#")

    private companion object {
        /** The banking app measured on the S24 FE (Patrice's): Crédit Coopératif, BPCE group (Sécur'Pass). */
        val MEASURED = setOf("com.credit_coop.android.mobilebanking")
        val DIGIT = Regex("\\d")
    }
}
