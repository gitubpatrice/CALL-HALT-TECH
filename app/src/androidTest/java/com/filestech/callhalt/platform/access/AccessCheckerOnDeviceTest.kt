package com.filestech.callhalt.platform.access

import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.filestech.callhalt.platform.debug.MeasureNotificationListener
import com.filestech.callhalt.platform.notification.NotificationListener
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [AndroidAccessChecker] reads of the phone's special accesses, granted then taken back as the user would in the
 * settings — here through the shell, which the JVM has no equivalent of. The runtime permissions are left out:
 * taking one back kills the process, this test's included.
 */
@RunWith(AndroidJUnit4::class)
class AccessCheckerOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val checker = AndroidAccessChecker(context)
    private val listener = component<NotificationListener>()

    /**
     * Before each test as well: an access left granted on the device (a measurement, the app installed by hand) made the
     * second listener's test fail (measured on the Android 16 emulator, 2026-11-03).
     */
    @Before
    @After
    fun takeBack() {
        shell("cmd deviceidle whitelist -${context.packageName}")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW default")
        shell("cmd notification disallow_listener $listener")
        shell("cmd notification disallow_listener ${component<MeasureNotificationListener>()}")
    }

    @Test
    fun displayOverOtherApps_isReadAsTheSettingsLeaveIt() {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        assertThat(checker.granted()).contains(Access.SCREEN)
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW ignore")
        assertThat(checker.granted()).doesNotContain(Access.SCREEN)
    }

    /** The list the phone's dialog writes to, and its battery page ("Unrestricted"): measured the same on the S24 FE. */
    @Test
    fun neverAsleep_isReadAsTheBatterySettingsLeaveIt() {
        shell("cmd deviceidle whitelist +${context.packageName}")
        assertThat(checker.granted()).contains(Access.BACKGROUND)
        shell("cmd deviceidle whitelist -${context.packageName}")
        assertThat(checker.granted()).doesNotContain(Access.BACKGROUND)
    }

    @Test
    fun notificationAccess_isReadAsTheSettingsLeaveIt() {
        shell("cmd notification allow_listener $listener")
        assertThat(checker.granted()).contains(Access.NOTIFICATIONS)
        shell("cmd notification disallow_listener $listener")
        assertThat(checker.granted()).doesNotContain(Access.NOTIFICATIONS)
    }

    /**
     * The test version's second listener reads nothing the protection uses: granted alone, the access is missing — once
     * the phone counts the PACKAGE as a listener. It writes that list a moment later (measured on Android 14): checked
     * before, a check by package would pass too.
     */
    @Test
    fun notificationAccess_isTheProtectionListenersOnly() {
        shell("cmd notification allow_listener ${component<MeasureNotificationListener>()}")
        val deadline = System.currentTimeMillis() + PACKAGE_LIST_TIMEOUT_MILLIS
        while (context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(POLL_MILLIS)
        }
        assertThat(NotificationManagerCompat.getEnabledListenerPackages(context)).contains(context.packageName)
        assertThat(checker.granted()).doesNotContain(Access.NOTIFICATIONS)
    }

    private inline fun <reified T> component(): String = ComponentName(context, T::class.java).flattenToString()

    /** Runs [command] as the shell and waits for its end: the descriptor closes when the command is done. */
    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private companion object {
        const val PACKAGE_LIST_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
