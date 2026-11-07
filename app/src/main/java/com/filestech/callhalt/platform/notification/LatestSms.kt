package com.filestech.callhalt.platform.notification

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import java.time.Duration
import java.time.Instant

/** What [readLatestSms] read: the latest message, and when it was sent (0 when the notification does not say). */
class LatestSms(val message: SmsMessage, val sentAtMillis: Long)

/**
 * Reads, of a notification of the default SMS app, what the code rule needs (docs/ARCHITECTURE.md, D-021): its latest
 * message — the last entry of the conversation alone, never the ones before it, nor anything else when that entry has
 * no text (a photo) — else the notification's own text. Null for a group summary (its messages come as their own
 * notifications), a notification without text, or a message older than [MAX_MESSAGE_AGE]: a code reposted hours later
 * (a snooze that ends, an update of the notification) is not a code received now. [maskedText] is the text Android puts
 * in place of a message it hides, in the phone's language, or null where it has none.
 */
internal fun readLatestSms(notification: Notification, maskedText: String?, at: Instant): LatestSms? {
    val extras = notification.extras
    val latest = BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Parcelable::class.java)
        ?.lastOrNull() as? Bundle
    val text = if (latest != null) latest.getCharSequence(MESSAGE_TEXT) else ownText(extras)
    val sentAt = if (latest != null) latest.getLong(MESSAGE_TIME) else notification.`when`
    val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    if (summary || text == null || isOlderThan(sentAt, at.minus(MAX_MESSAGE_AGE))) return null
    val message = if (maskedText != null && text.toString() == maskedText) SmsMessage.Masked else SmsMessage.Text(text)
    return LatestSms(message, sentAt)
}

/**
 * [read], or — when it throws — a message read as masked (coherence audit of 2026-11-07, C1). A notification of the
 * default SMS app shaped in a way the reading does not expect (a maker's own extras) must not let a code pass in silence:
 * a masked message is a code to the rule, the protective side — a STOP too many at worst, and only during a call or just
 * after. Its time unknown (0), it is never taken for a repost. [failed] logs the failure.
 */
// A catch-all, by design: another app's notification is read here, and nothing in it may pass for "no code".
@Suppress("TooGenericExceptionCaught")
internal inline fun latestSmsOrMasked(read: () -> LatestSms?, failed: (Exception) -> Unit): LatestSms? = try {
    read()
} catch (failure: Exception) {
    failed(failure)
    LatestSms(SmsMessage.Masked, sentAtMillis = 0)
}

/**
 * Past this age, a message is not a code received now. Generous: a network may deliver an SMS minutes late, and a
 * message's time can come from the sender's network clock; a code ten minutes old has most often expired anyway.
 */
internal val MAX_MESSAGE_AGE: Duration = Duration.ofMinutes(MAX_MESSAGE_AGE_MINUTES)

private const val MAX_MESSAGE_AGE_MINUTES = 10L

/** The full text of a BigTextStyle notification first, the one line otherwise. */
private fun ownText(extras: Bundle): CharSequence? =
    extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)

private fun isOlderThan(sentAtMillis: Long, limit: Instant): Boolean =
    sentAtMillis > 0 && Instant.ofEpochMilli(sentAtMillis).isBefore(limit)

// The keys of a MessagingStyle message, the same in the platform and in androidx since API 24 (Notification.java,
// NotificationCompat.java): read directly, so that the messages before the latest are never even built.
private const val MESSAGE_TEXT = "text"
private const val MESSAGE_TIME = "time"
