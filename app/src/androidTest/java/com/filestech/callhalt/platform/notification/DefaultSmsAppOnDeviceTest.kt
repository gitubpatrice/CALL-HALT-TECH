package com.filestech.callhalt.platform.notification

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The default SMS app is visible to the app, through the manifest's `<queries>`. From Android 11 on, without them,
 * `getDefaultSmsPackage` answers none and the listener cannot tell an SMS from any other notification (measured on the
 * S24 FE and on Android 16, 2026-11-03; red on Android 16 without the query, and with the query its documentation asks
 * for, SMS_DELIVER: a protected broadcast, which AOSP's AppsFilterUtils ignores when it matches receivers). Skipped
 * before Android 11, where every app is visible — the CI's emulator: run it on a newer one. A device with the SMS role
 * but no SMS app would fail it; none here has one.
 */
@RunWith(AndroidJUnit4::class)
class DefaultSmsAppOnDeviceTest {

    @Test
    fun theDefaultSmsAppIsSeen() {
        assumeTrue("Every app is visible before Android 11", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roles = context.getSystemService(RoleManager::class.java)
        assumeTrue("No SMS app on this device", roles.isRoleAvailable(RoleManager.ROLE_SMS))
        assertThat(Telephony.Sms.getDefaultSmsPackage(context)).isNotNull()
    }
}
