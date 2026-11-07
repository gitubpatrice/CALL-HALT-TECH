package com.filestech.callhalt.platform.notification

import android.content.Context
import android.provider.Telephony
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the default SMS app hides its messages' text in its notifications, and so every code (publication audit,
 * FN-2; docs/ARCHITECTURE.md, D-037): SMS Tech set to hide its previews writes "New message" in place of each one. The
 * home screen says so, in Patrice's words, with what to do. Not Android's own masking ([SmsMessage.Masked], a code
 * Android itself found and hid): that one is read as a code, so the code is still seen.
 *
 * Kept on this phone only: the package of the SMS app found hiding them, or nothing — never a message's text — so that
 * the home screen still says it after a restart, and stops saying it once another app is the default SMS app. Updated
 * by the notification listener at each message received in a conversation of the default SMS app.
 */
@Singleton
class SmsPreview @Inject constructor(@ApplicationContext private val context: Context) {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getString(KEY_HIDDEN_IN, null))

    /** The SMS app whose last message received came with its fixed text in place of the message; null otherwise. */
    val hiddenIn: StateFlow<String?> = state.asStateFlow()

    /** What [latest], read of a notification of the default SMS app [packageName], says of its previews. */
    fun seen(packageName: String, latest: LatestSms) {
        val now = hiddenInAfter(state.value, packageName, latest)
        if (now == state.value) return
        state.value = now
        preferences.edit { if (now == null) remove(KEY_HIDDEN_IN) else putString(KEY_HIDDEN_IN, now) }
    }

    /** The phone's default SMS app now: what [hiddenIn] is compared with, each time the home screen comes back. */
    fun defaultSmsApp(): String? = Telephony.Sms.getDefaultSmsPackage(context)

    private companion object {
        const val FILE = "sms_preview"
        const val KEY_HIDDEN_IN = "hidden_in"
    }
}

/**
 * The fixed text an SMS app writes in place of a message when it hides its previews, by package, in each of its
 * languages (the app's language may not be the phone's). SMS Tech's `notif_preview_hidden_content`, read in its
 * sources (`IncomingMessageNotifier`: its "when unlocked" and "never" settings both write it, locked or not — measured
 * unlocked on the Android 16 emulator). Samsung Messages and Google Messages are not here: their hidden previews are not
 * measured yet.
 */
internal val HIDDEN_PREVIEW_TEXTS: Map<String, Set<String>> = mapOf(
    "com.filestech.sms" to setOf("New message", "Nouveau message", "Neue Nachricht", "Nuovo messaggio", "Mensaje nuevo"),
)

/**
 * The SMS app hiding its previews after [latest] of [packageName]: only a message received in a conversation tells it
 * — its app's fixed text, that app; any other text, none. A notification without a conversation (a failed send, a
 * warning of the app) or the user's own reply carries the app's own words, not a message it hides; Android's masking
 * ([SmsMessage.Masked]) says nothing of the app's setting. Both leave [current] as it was.
 */
internal fun hiddenInAfter(current: String?, packageName: String, latest: LatestSms): String? {
    val message = latest.message
    if (!latest.receivedInConversation || message !is SmsMessage.Text) return current
    return packageName.takeIf { message.value.toString().trim() in HIDDEN_PREVIEW_TEXTS[packageName].orEmpty() }
}

/** Whether the home screen says it: the app found hiding its previews is still the default SMS app. */
internal fun previewHiddenNow(hiddenIn: String?, defaultSmsApp: String?): Boolean = hiddenIn != null && hiddenIn == defaultSmsApp
