package com.filestech.callhalt.platform.telephony

import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The values of [TelephonyManager.EXTRA_STATE] are computed by the platform at run time, so [PhoneState] cannot
 * name them on the JVM: it relies on their documented values. Read here on a real Android, they are the same.
 */
@RunWith(AndroidJUnit4::class)
class PhoneStateOnDeviceTest {

    @Test
    fun theStatesThePlatformSendsAreTheOnesReadByName() {
        assertThat(PhoneState.fromExtra(TelephonyManager.EXTRA_STATE_IDLE)).isEqualTo(PhoneState.IDLE)
        assertThat(PhoneState.fromExtra(TelephonyManager.EXTRA_STATE_RINGING)).isEqualTo(PhoneState.RINGING)
        assertThat(PhoneState.fromExtra(TelephonyManager.EXTRA_STATE_OFFHOOK)).isEqualTo(PhoneState.OFFHOOK)
    }
}
