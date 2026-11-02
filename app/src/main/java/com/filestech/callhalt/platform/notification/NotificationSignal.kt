package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import java.time.Instant

/**
 * The notification channels on which a banking app asks to approve an operation (docs/ARCHITECTURE.md, P-2).
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
 * What a notification posted by [packageName] on [channelId] is to the engine, if anything: stamped [at], the
 * phone's clock at reception (Signal's contract). The package goes to the journal, which app asked.
 */
fun notificationSignal(packageName: String, channelId: String?, at: Instant): SensitiveAction? =
    if (channelId in BANK_REQUEST_CHANNELS) {
        SensitiveAction(at, SensitiveActionKind.BANK_VALIDATION_REQUEST, source = packageName)
    } else {
        null
    }
