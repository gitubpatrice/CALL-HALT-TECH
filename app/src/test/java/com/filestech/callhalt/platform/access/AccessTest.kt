package com.filestech.callhalt.platform.access

import android.content.pm.PackageInstaller
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Where the activation shows its way through the "restricted settings" (measured on 2026-11-02, docs/ARCHITECTURE.md,
 * P-3): missing where the phone refuses, the user is stuck; shown where it does not, it costs a few lines.
 */
class AccessTest {

    @Test
    fun `an app installed from a file - notification access from Android 13, the STOP screen from Android 15`() {
        val file = PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
        assertThat(restricted(Build.VERSION_CODES.TIRAMISU, file)).containsExactly(Access.NOTIFICATIONS)
        // Measured: Android 14 grants "display over other apps" at once, Android 16 refuses it.
        assertThat(restricted(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, file)).containsExactly(Access.NOTIFICATIONS)
        assertThat(restricted(Build.VERSION_CODES.BAKLAVA, file)).containsExactly(Access.SCREEN, Access.NOTIFICATIONS)
        // Not measured: assumed, so that the help shows where it may be needed.
        assertThat(restricted(Build.VERSION_CODES.VANILLA_ICE_CREAM, file)).containsExactly(Access.SCREEN, Access.NOTIFICATIONS)
    }

    @Test
    fun `the phone's permission dialog is never restricted`() {
        assertThat(mayBeRestricted(Access.CALLS, Build.VERSION_CODES.BAKLAVA, PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)).isFalse()
    }

    @Test
    fun `a store escapes, any other source keeps the help`() {
        val sdk = Build.VERSION_CODES.BAKLAVA
        assertThat(restricted(sdk, PackageInstaller.PACKAGE_SOURCE_STORE)).isEmpty()
        // adb (measured: OTHER, not restricted), an installer that says nothing (UNSPECIFIED; F-Droid's source is not
        // measured, P-3), a download.
        listOf(
            PackageInstaller.PACKAGE_SOURCE_OTHER,
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED,
            PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE,
        ).forEach { source -> assertThat(restricted(sdk, source)).containsExactly(Access.SCREEN, Access.NOTIFICATIONS) }
    }

    @Test
    fun `before Android 13 - no install source, no restricted settings`() {
        assertThat(restricted(Build.VERSION_CODES.S_V2, packageSource = null)).isEmpty()
    }

    private fun restricted(sdk: Int, packageSource: Int?): List<Access> =
        Access.entries.filter { mayBeRestricted(it, sdk, packageSource) }
}
