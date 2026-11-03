package com.filestech.callhalt.platform.notification

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.SensorQueue
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider

/**
 * Sees the notifications of every app, once the user grants notification access (a restricted setting for an app
 * installed from a file, docs/ARCHITECTURE.md, P-3), and hands the ones [notificationSignal] recognises to the
 * [SensorQueue]. Of each notification it reads the app and the channel; of the default SMS app's, the latest
 * message too, in memory, to recognise a validation code (D-021) — of no other app's, the title or the text
 * (PRIVACY.md).
 *
 * Bound by the system, which keeps the process alive while it is: no `goAsync()`, the queue's work runs in the
 * application's scope. The callbacks come on the main thread: the channel is compared first, and only the SMS app's
 * notifications are read further — the default SMS app itself is looked up once a minute, not for every notification
 * (the lookup costs Android a few package queries).
 *
 * A notification can arrive more than once: the system delivers one twice, 0.3 s apart (measured on Android 16 and
 * 17), and an app may update its own. For a bank's request, the listener cannot tell them apart: the engine absorbs a
 * repeat only while the STOP it caused is unanswered, for 60 s at most, and a later repeat shows a new STOP — the
 * protective side (NotificationSignalTest; measured on the S24 FE, the bank posts its request once). A code SMS has an
 * identity, its conversation and its time: the same message posted again is not a new code.
 */
@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    /** Cheap to build: a channel and one coroutine. */
    @Inject lateinit var queue: SensorQueue

    /** Resolved in the queue, never at injection time: it reaches the database (Agenda Tech, audit F2). */
    @Inject lateinit var processSignal: Provider<ProcessSignalUseCase>

    // All of what follows is touched on the main thread only, where the callbacks come.
    private var smsApp: String? = null
    private var smsAppReadAt: Long? = null
    private var lastCode: Pair<String, Long>? = null
    private var maskedTextCache: Pair<LocaleList, String>? = null

    override fun onListenerConnected() {
        // Debug builds only (no tree in release): for the measurements, when the system binds the listener.
        Timber.d("Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // The phone's clock at reception: the time every signal carries (Signal's contract).
        val at = Instant.now()
        val channel = sbn.notification.channelId
        val sms = if (channel !in BANK_REQUEST_CHANNELS && sbn.packageName == defaultSmsApp()) latestSms(sbn, at) else null
        val signal = notificationSignal(sbn.packageName, channel, sms?.message, at) ?: return
        if (signal.kind == SensitiveActionKind.CODE_MESSAGE && sms != null && isRepost(sbn.key, sms.sentAtMillis)) return
        // The kind and the app, never the text.
        Timber.d("%s from %s", signal.kind, sbn.packageName)
        queue.submit(reason = "notification ${signal.kind}") { processSignal.get()(signal) }
    }

    /**
     * Looked up once a minute. It sees only an SMS app the app may see (the manifest's `<queries>`): without them, the
     * answer was none — measured on Android 16 and on the S24 FE — and a code SMS was read only because Android shows a
     * listener the app of each notification it delivers. An answer of none is still never kept: on Android 16, before
     * the `<queries>`, a kept "none" left three code SMS of the next minute unread; a phone may also have no SMS app yet.
     */
    private fun defaultSmsApp(): String? {
        val now = SystemClock.elapsedRealtime()
        if (smsApp == null || smsAppReadAt.let { it == null || now - it > SMS_APP_LOOKUP_MS }) {
            smsApp = Telephony.Sms.getDefaultSmsPackage(this)
            smsAppReadAt = now
            Timber.d("Default SMS app: %s", smsApp)
        }
        return smsApp
    }

    // A catch-all, by design: another app's notification is read here, and nothing in it may stop the listener.
    @Suppress("TooGenericExceptionCaught")
    private fun latestSms(sbn: StatusBarNotification, at: Instant): LatestSms? = try {
        readLatestSms(sbn.notification, maskedText(), at)
    } catch (failure: Exception) {
        // The failure's class only: its message could quote the notification.
        Timber.w("SMS notification unreadable: %s", failure.javaClass.name)
        null
    }

    /** The same message of the same conversation, posted again (an update, a re-delivery): not a new code. */
    private fun isRepost(key: String, sentAtMillis: Long): Boolean {
        val identity = key to sentAtMillis
        if (sentAtMillis > 0 && identity == lastCode) return true
        lastCode = identity
        return false
    }

    /**
     * The text Android puts in place of a message it hides from listeners, in the PHONE's language — not the app's,
     * which the user may set apart (measured on Android 16 and 17: "Sensitive notification content hidden", "Le contenu
     * sensible de la notification a été masqué"). Android hides messages from Android 15 on; before, or without the
     * text, null: nothing is taken for it.
     */
    private fun maskedText(): String? {
        val id = maskedTextId
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || id == 0) return null
        val locales = getSystemService(LocaleManager::class.java).systemLocales
        val known = maskedTextCache?.takeIf { (forLocales, _) -> forLocales == locales }
        val entry = known ?: (locales to inLanguages(locales).getString(id))
        maskedTextCache = entry
        return entry.second
    }

    // Not a change of the app's language: the phone's own, to read one framework text. No App Bundle is built (D-016).
    @SuppressLint("AppBundleLocaleChanges")
    private fun inLanguages(locales: LocaleList): Context {
        val configuration = Configuration()
        configuration.setLocales(locales)
        return createConfigurationContext(configuration)
    }

    private val maskedTextId: Int by lazy { frameworkStringId(MASKED_TEXT_RESOURCE) }

    // A framework text Android does not publish: looked up by its name, 0 where the system has none.
    @SuppressLint("DiscouragedApi")
    private fun frameworkStringId(name: String): Int = Resources.getSystem().getIdentifier(name, "string", "android")

    private companion object {
        /** Its name in AOSP: in the framework strings of API 35 to 37, not 34; read in Android 16's resources. */
        const val MASKED_TEXT_RESOURCE = "redacted_notification_message"

        /** A change of SMS app is seen within a minute. */
        const val SMS_APP_LOOKUP_MS = 60_000L
    }
}
