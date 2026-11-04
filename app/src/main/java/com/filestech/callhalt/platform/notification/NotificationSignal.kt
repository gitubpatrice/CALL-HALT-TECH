package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The notification channels on which a banking app asks to approve an operation (docs/ARCHITECTURE.md, D-019).
 *
 * Recognised by the CHANNEL, never by the words: a channel is the identifier the app gave in its code, the same in
 * every language and every version of its texts, and reading it reads nothing of what the notification says. From
 * any app: Sécur'Pass is common to the BPCE group's banks, and a channel name no other app uses costs nothing
 * where it does not appear — a false STOP on the protective side at worst, never a request missed for a package
 * name nobody measured.
 */
internal val BANK_REQUEST_CHANNELS = setOf(
    // Sécur'Pass, measured on the S24 FE on 2026-11-02: the Crédit Coopératif app (BPCE group), "Vous allez
    // enregistrer un compte externe", empty text, no button. Other banks of the group: to measure.
    "securpass_id",
)

/**
 * What the listener read of a notification of the default SMS app — of no other app (docs/ARCHITECTURE.md, D-021):
 * its latest message, never the conversation before it.
 */
sealed interface SmsMessage {
    data class Text(val value: CharSequence) : SmsMessage {
        /** Never the text: a code must not reach a log, an exception or a report through a careless "$message". */
        override fun toString(): String = "Text(…)"
    }

    /** Android hid it from listeners: its own detector found a code in it (Android 15+; measured: English codes). */
    data object Masked : SmsMessage
}

/**
 * What a notification posted by [packageName] on [channelId] is to the engine, if anything: a banking app's request,
 * by its channel; or, when it comes from the default SMS app ([sms] then non-null), a validation code. Stamped [at],
 * the phone's clocks at reception (Signal's contract). The package goes to the journal: which app it came from. Of
 * the text, nothing is kept.
 */
fun notificationSignal(packageName: String, channelId: String?, sms: SmsMessage?, at: Moment): SensitiveAction? {
    val kind = when {
        channelId in BANK_REQUEST_CHANNELS -> SensitiveActionKind.BANK_VALIDATION_REQUEST
        sms == SmsMessage.Masked -> SensitiveActionKind.CODE_MESSAGE
        sms is SmsMessage.Text && isValidationCode(sms.value) -> SensitiveActionKind.CODE_MESSAGE
        else -> return null
    }
    return SensitiveAction(at, kind, source = packageName)
}
