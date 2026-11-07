package com.filestech.callhalt.platform.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SmsPreview] itself, on Android (GPT review of 2026-11-07, 4): what it is told is kept in its preferences, and found by
 * another instance — as the one built after a restart reads it — and a message shown again clears it. Within one
 * process, Android shares the preferences' memory: the file itself is Android's to write.
 */
@RunWith(AndroidJUnit4::class)
class SmsPreviewOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var before: String? = null

    @Before
    fun keep() {
        before = SmsPreview(context).hiddenIn.value
    }

    @After
    fun restore() {
        val preview = SmsPreview(context)
        val back = before
        if (back == null) preview.seen(SMS_TECH, received("Bonjour")) else preview.seen(back, received("New message"))
    }

    @Test
    fun theAppHidingItsPreviewsIsKeptForAnotherInstance_andClearedByAMessageShown() {
        SmsPreview(context).seen(SMS_TECH, received("Nouveau message"))
        assertThat(SmsPreview(context).hiddenIn.value).isEqualTo(SMS_TECH)

        SmsPreview(context).seen(SMS_TECH, received("Bonjour, rappelle-moi"))
        assertThat(SmsPreview(context).hiddenIn.value).isNull()
    }

    private fun received(text: String) = LatestSms(SmsMessage.Text(text), sentAtMillis = 0, receivedInConversation = true)

    private companion object {
        const val SMS_TECH = "com.filestech.sms"
    }
}
