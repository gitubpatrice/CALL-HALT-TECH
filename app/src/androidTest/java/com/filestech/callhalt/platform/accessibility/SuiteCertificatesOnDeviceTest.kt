package com.filestech.callhalt.platform.accessibility

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a phone with Google's suites (an emulator with Google's apps; not the CI's, which has none, nor a Samsung), each
 * suite installed is signed with one of the certificates written in [SUITE_CERTIFICATES], asked as the sensor asks
 * ([isSignedWith], M4): a digit copied wrong would leave Google's suite a STOP, and the JVM tests would not see it. And a
 * certificate altered by one bit is not its own. The copies on Play are signed with the other key of each suite, which no
 * phone here carries: read on APKMirror, not measured.
 */
@RunWith(AndroidJUnit4::class)
class SuiteCertificatesOnDeviceTest {

    private val packages: PackageManager = ApplicationProvider.getApplicationContext<Context>().packageManager

    @Test
    fun theSuitesInstalledAreSignedWithACertificateWritten() {
        val installed = SUITE_CERTIFICATES.filterKeys(::isInstalled)
        assumeTrue("no Google suite on this phone", installed.isNotEmpty())

        installed.forEach { (pkg, certificates) ->
            val own = certificates.map(String::hexToByteArray).filter { packages.isSignedWith(pkg, it) }
            assertThat(own).hasSize(1)
            val altered = own.single().copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            assertThat(packages.isSignedWith(pkg, altered)).isFalse()
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packages.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
