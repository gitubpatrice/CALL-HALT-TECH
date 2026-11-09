package com.filestech.callhalt.platform.stop

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A STOP request and screen, through Android's own bundles: the request crosses the intent that starts the screen, or the
 * notification's; the screen's session crosses a rebuild (a rotation, the process died while the phone asked for the
 * unlock). What is lost there is lost silently — the app to uninstall (FN-3, D-039) among the rest. Written to a parcel
 * and read back, as Android does when the process goes.
 */
@RunWith(AndroidJUnit4::class)
class StopSessionBundleOnDeviceTest {

    private val installed = StopRequest(
        id = 7,
        trigger = SensitiveActionKind.REMOTE_CONTROL_INSTALLED,
        onCall = true,
        channel = CallChannel.CELLULAR,
        earlier = listOf(SensitiveActionKind.CODE_MESSAGE),
        installedApp = "com.anydesk.anydeskandroid",
    )

    @Test
    fun aRequestCrossesItsIntentWhole() {
        val intent = Intent(Intent.ACTION_MAIN)
        installed.writeTo(intent)

        assertThat(StopRequest.fromBundle(throughParcel(requireNotNull(intent.extras)))).isEqualTo(installed)
        assertThat(StopRequest.fromBundle(throughParcel(installed.copy(installedApp = null).toBundle()))?.installedApp).isNull()
    }

    @Test
    fun aSessionCrossesARebuildWhole_theAppToUninstallIncluded() {
        val code = StopRequest(8, SensitiveActionKind.CODE_MESSAGE, onCall = false, CallChannel.CELLULAR)
        val session = StopSession.of(installed).replacedBy(code).answering(opening = StopChoice.UNINSTALL)

        val rebuilt = StopSession.fromBundle(throughParcel(session.toBundle()))

        assertThat(rebuilt).isEqualTo(session)
        assertThat(rebuilt?.uninstallable).isEqualTo("com.anydesk.anydeskandroid")
    }

    private fun throughParcel(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}
