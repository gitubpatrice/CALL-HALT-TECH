package com.filestech.callhalt.platform.notification

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [ListenerConnection.rebind] does to the listener on a real phone (D-025): turned off and back on, it keeps the
 * notification access the user granted, comes back enabled, and is bound again by the system. The JVM has no system to
 * answer it.
 */
@RunWith(AndroidJUnit4::class)
class ListenerConnectionOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val listener = ComponentName(context, NotificationListener::class.java)

    @Before
    fun grant() {
        shell("cmd notification allow_listener ${listener.flattenToString()}")
    }

    @After
    fun takeBack() {
        shell("cmd notification disallow_listener ${listener.flattenToString()}")
    }

    @Test
    fun rebind_keepsTheAccessAndBindsTheListenerAgain() {
        waitUntil { bound() }

        ListenerConnection(context).rebind()

        assertThat(context.packageManager.getComponentEnabledSetting(listener))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        assertThat(context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener))
            .isTrue()
        assertThat(waitUntil { bound() }).isTrue()
    }

    /** The system's own list of the listeners it holds bound, as `dumpsys notification` prints it. */
    private fun bound(): Boolean = shell("dumpsys notification")
        .substringAfter("Live notification listeners", "")
        .substringBefore("Snoozed notification listeners")
        .contains(listener.flattenToString().replace("/.", "/${listener.packageName}."))

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + BIND_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return condition()
    }

    /** Runs [command] as the shell and returns what it printed. */
    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
    }

    private companion object {
        const val BIND_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 100L
    }
}
