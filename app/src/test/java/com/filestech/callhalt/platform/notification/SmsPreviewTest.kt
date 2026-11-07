package com.filestech.callhalt.platform.notification

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** FN-2: SMS Tech set to hide its previews writes its fixed text in place of every message, so of every code. */
class SmsPreviewTest {

    @Test
    fun `SMS Tech's fixed text, in each of its languages, means its previews are hidden`() {
        listOf("New message", "Nouveau message", "Neue Nachricht", "Nuovo messaggio", "Mensaje nuevo").forEach { text ->
            assertThat(hiddenInAfter(current = null, SMS_TECH, received(text))).isEqualTo(SMS_TECH)
        }
    }

    @Test
    fun `any other message received means its previews show again`() {
        assertThat(hiddenInAfter(current = SMS_TECH, SMS_TECH, received("Votre code : 482913"))).isNull()
    }

    /** A failed send, a warning of the SMS app: its own words, not a message it hides or shows (review of 2026-11-07, 1). */
    @Test
    fun `a notification without a conversation leaves what was known`() {
        val failedSend = LatestSms(SmsMessage.Text("Échec de l'envoi"), sentAtMillis = 0, receivedInConversation = false)
        assertThat(hiddenInAfter(current = SMS_TECH, SMS_TECH, failedSend)).isEqualTo(SMS_TECH)
        assertThat(hiddenInAfter(current = null, SMS_TECH, failedSend)).isNull()
    }

    /** The same words from another app are a message like any other: another app's setting is not known. */
    @Test
    fun `another app's message with the same words is a message`() {
        assertThat(hiddenInAfter(current = null, GOOGLE_MESSAGES, received("Nouveau message"))).isNull()
    }

    /** Android's own masking says nothing of the app's setting: what was known stays. */
    @Test
    fun `Android's own masking leaves what was known`() {
        val masked = LatestSms(SmsMessage.Masked, sentAtMillis = 0, receivedInConversation = true)
        assertThat(hiddenInAfter(current = SMS_TECH, SMS_TECH, masked)).isEqualTo(SMS_TECH)
        assertThat(hiddenInAfter(current = null, SMS_TECH, masked)).isNull()
    }

    @Test
    fun `the fixed text is recognised with spaces around it`() {
        assertThat(hiddenInAfter(current = null, SMS_TECH, received(" Nouveau message "))).isEqualTo(SMS_TECH)
    }

    /** Another default SMS app since (review of 2026-11-07, 3): the home screen no longer says it. */
    @Test
    fun `the home screen says it only while that app is the default SMS app`() {
        assertThat(previewHiddenNow(hiddenIn = SMS_TECH, defaultSmsApp = SMS_TECH)).isTrue()
        assertThat(previewHiddenNow(hiddenIn = SMS_TECH, defaultSmsApp = GOOGLE_MESSAGES)).isFalse()
        assertThat(previewHiddenNow(hiddenIn = SMS_TECH, defaultSmsApp = null)).isFalse()
        assertThat(previewHiddenNow(hiddenIn = null, defaultSmsApp = SMS_TECH)).isFalse()
    }

    private fun received(text: String) = LatestSms(SmsMessage.Text(text), sentAtMillis = 0, receivedInConversation = true)

    private companion object {
        const val SMS_TECH = "com.filestech.sms"
        const val GOOGLE_MESSAGES = "com.google.android.apps.messaging"
    }
}
