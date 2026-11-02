package com.filestech.callhalt.platform.notification

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * What [readLatestSms] reads of a real Android notification — built as an SMS app builds it, with androidx (as SMS Tech
 * does) — which the JVM cannot build: the last message of a conversation alone, its time, Android's masking, and what
 * it must not take for a new message.
 */
@RunWith(AndroidJUnit4::class)
class LatestSmsOnDeviceTest {

    private val now: Instant = Instant.now()

    @Test
    fun theLatestMessageOfAConversationIsRead_neverTheOnesBefore() {
        val notification = conversation(
            "Votre code de validation est 482913" to now.minusSeconds(120),
            "Rappelle-moi quand tu peux" to now,
        )
        val latest = checkNotNull(readLatestSms(notification, maskedText = null, now))
        assertThat((latest.message as SmsMessage.Text).value.toString()).isEqualTo("Rappelle-moi quand tu peux")
        assertThat(latest.sentAtMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun aMessageAndroidReplacedWithItsMaskingTextIsMasked() {
        val notification = conversation(MASKED to now)
        assertThat(readLatestSms(notification, maskedText = MASKED, now)?.message).isEqualTo(SmsMessage.Masked)
        // The same words, on a phone without masking: a text like any other.
        assertThat(readLatestSms(notification, maskedText = null, now)?.message).isInstanceOf(SmsMessage.Text::class.java)
    }

    @Test
    fun aMessageOlderThanTheLimitIsNotOneReceivedNow() {
        val sent = now.minus(MAX_MESSAGE_AGE).minusSeconds(1)
        assertThat(readLatestSms(conversation("Code de confirmation : 271846" to sent), maskedText = null, now)).isNull()
    }

    @Test
    fun aGroupSummaryIsNotRead() {
        val summary = builder()
            .setStyle(style("Code de confirmation : 271846" to now))
            .setGroup("sms")
            .setGroupSummary(true)
            .build()
        assertThat(readLatestSms(summary, maskedText = null, now)).isNull()
    }

    @Test
    fun withoutAConversation_theFullTextOfABigTextNotificationIsRead() {
        val notification = builder()
            .setContentText("Nouveau message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Votre code de validation est 482913"))
            .setWhen(now.toEpochMilli())
            .build()
        val latest = readLatestSms(notification, maskedText = null, now)
        assertThat((latest?.message as SmsMessage.Text).value.toString()).isEqualTo("Votre code de validation est 482913")
    }

    private fun conversation(vararg messages: Pair<String, Instant>): Notification =
        builder().setStyle(style(*messages)).build()

    private fun style(vararg messages: Pair<String, Instant>) =
        NotificationCompat.MessagingStyle(Person.Builder().setName("Moi").build()).also { style ->
            val sender = Person.Builder().setName("Banque").build()
            messages.forEach { (text, sent) -> style.addMessage(text, sent.toEpochMilli(), sender) }
        }

    private fun builder() =
        NotificationCompat.Builder(ApplicationProvider.getApplicationContext(), "incoming_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)

    private companion object {
        const val MASKED = "Le contenu sensible de la notification a été masqué"
    }
}
