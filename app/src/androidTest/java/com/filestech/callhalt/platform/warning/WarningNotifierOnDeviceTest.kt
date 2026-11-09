package com.filestech.callhalt.platform.warning

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The warning of a remote-control app installed, as Android receives it (GPT review of 2026-11-09, 3): posted with a STOP
 * ([Decision.ShowWarning.withStop]), it reaches the phone silent — no sound, no vibration, its group alerting for the
 * summary only, which keeps it from showing over the STOP — and the ordinary one does not; withdrawn, it is gone. The
 * engine's tests see only the decision. Measured besides on Android 16: no banner over the STOP, none of its sound.
 */
@RunWith(AndroidJUnit4::class)
class WarningNotifierOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = WarningNotifier(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun channel() {
        notifier.createChannel()
    }

    @After
    fun withdraw() {
        notifier.withdraw(ANYDESK)
    }

    @Test
    fun aWarningWithAStopIsSilent_theOrdinaryOneIsNot() {
        assertThat(notifier.show(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK, withStop = true))).isTrue()
        val silent = posted()
        assertThat(silent.groupAlertBehavior).isEqualTo(Notification.GROUP_ALERT_SUMMARY)
        assertThat(silent.sound).isNull()
        assertThat(silent.vibrate).isNull()

        assertThat(notifier.show(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK))).isTrue()
        assertThat(posted().groupAlertBehavior).isEqualTo(Notification.GROUP_ALERT_ALL)
    }

    @Test
    fun aWarningWithdrawnIsGone() {
        notifier.show(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK, withStop = true))
        notifier.withdraw(ANYDESK)
        assertThat(manager.activeNotifications.filter { it.tag == ANYDESK }).isEmpty()
    }

    private fun posted(): Notification = manager.activeNotifications.single { it.tag == ANYDESK }.notification

    private companion object {
        const val ANYDESK = "com.anydesk.anydeskandroid"
    }
}
