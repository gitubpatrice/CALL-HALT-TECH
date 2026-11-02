package com.filestech.callhalt.platform.debug

import android.app.Notification
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber

/**
 * DEBUG BUILDS ONLY (src/debug): two measurements (docs/ARCHITECTURE.md, § 6 and § 7). P-2 — can the app read a banking
 * app's request to approve an operation? And the code SMS — what does a listener see of the default SMS app's
 * notification, now that Android 15+ hides codes from listeners? It logs what Android hands a notification listener,
 * for the MEASURED packages and the default SMS app only, to the phone's own log (logcat, on the developer's
 * computer), digits masked. Nothing is stored, nothing is decided: the real sensors are designed from what this shows.
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
        if (!measured(sbn.packageName)) return
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
        // An SMS app files the message in a conversation (MessagingStyle), not only in the text.
        Timber.i("P-2 messages: %s", messages(extras).map(::masked))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (!measured(sbn.packageName)) return
        // REASON_CLICK (1): the user tapped it — idea 15 of PROJET.md § 9.2, "the press on Validate".
        Timber.i("P-2 removed: package=%s id=%d reason=%d", sbn.packageName, sbn.id, reason)
    }

    /**
     * The words, which the sensor will be designed from, without the digits: no amount, IBAN or code in the log
     * (security review of 2026-11-02). A redacted text (Android 15+ hides codes from listeners) reads as it is.
     */
    private fun masked(text: CharSequence?): String? = text?.toString()?.replace(DIGIT, "#")

    private fun measured(packageName: String): Boolean =
        packageName in MEASURED || packageName == Telephony.Sms.getDefaultSmsPackage(this)

    /** The texts of a MessagingStyle conversation: each message is a Bundle whose text is under "text". */
    @Suppress("DEPRECATION") // The typed getter needs API 33; this runs from API 29.
    private fun messages(extras: Bundle): List<CharSequence?> =
        extras.getParcelableArray(Notification.EXTRA_MESSAGES).orEmpty().map { (it as? Bundle)?.getCharSequence("text") }

    private companion object {
        /** The banking app measured on the S24 FE (Patrice's): Crédit Coopératif, BPCE group (Sécur'Pass). */
        val MEASURED = setOf("com.credit_coop.android.mobilebanking")
        val DIGIT = Regex("\\d")
    }
}
