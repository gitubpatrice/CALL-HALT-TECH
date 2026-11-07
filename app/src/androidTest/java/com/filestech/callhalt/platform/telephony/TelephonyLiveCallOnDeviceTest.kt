package com.filestech.callhalt.platform.telephony

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.Moment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * On Android, the phone's own answers (D-035): the read reaches the phone, on every version the app supports — down to
 * Android 10, where the CI runs it — and a phone with no call says so (GPT review of 2026-11-07, 4: the JVM tests would
 * stay green on an adapter that never asked). A call itself is measured on the emulator, not here: the CI cannot place
 * one.
 */
@RunWith(AndroidJUnit4::class)
class TelephonyLiveCallOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun withNoCallThePhoneSaysThereIsNone() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.READ_PHONE_STATE)
        val at = Moment(Instant.now())

        assertThat(TelephonyLiveCall(context).stateAt(at)).isEqualTo(CallEnded(at, CallChannel.CELLULAR))
    }
}
