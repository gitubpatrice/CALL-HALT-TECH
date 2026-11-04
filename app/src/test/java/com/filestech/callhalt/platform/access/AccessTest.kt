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
    fun `the phone's own dialogs are never restricted`() {
        listOf(Access.CALLS, Access.BACKGROUND, Access.ALERTS).forEach { access ->
            assertThat(mayBeRestricted(access, Build.VERSION_CODES.BAKLAVA, PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE))
                .isFalse()
        }
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

    /** D-024: before Android 13 an app posts notifications without asking; the step then opens the settings. */
    @Test
    fun `the notifications are a question of the phone from Android 13 only`() {
        assertThat(runtimePermissions(Access.ALERTS, Build.VERSION_CODES.TIRAMISU))
            .asList().containsExactly(android.Manifest.permission.POST_NOTIFICATIONS)
        assertThat(runtimePermissions(Access.ALERTS, Build.VERSION_CODES.S_V2)).isNull()
        assertThat(runtimePermissions(Access.CALLS, Build.VERSION_CODES.Q)).isEqualTo(CALL_PERMISSIONS)
        listOf(Access.SCREEN, Access.BACKGROUND, Access.NOTIFICATIONS).forEach { access ->
            assertThat(runtimePermissions(access, Build.VERSION_CODES.BAKLAVA)).isNull()
        }
    }

    /**
     * An "Android Go" phone (P-21, read in AOSP): before Android 14 its Settings refuse the display over the others (and
     * Android 10 refuses every notification listener); from Android 14, the page the app opens grants it (measured on the
     * Android 16 emulator in low-memory mode, the STOP shown from the background).
     */
    @Test
    fun `an Android Go phone can be protected from Android 14`() {
        listOf(Build.VERSION_CODES.Q, Build.VERSION_CODES.R, Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU)
            .forEach { sdk -> assertThat(phoneCanProtect(lowRam = true, sdk = sdk)).isFalse() }
        listOf(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Build.VERSION_CODES.VANILLA_ICE_CREAM, Build.VERSION_CODES.BAKLAVA)
            .forEach { sdk -> assertThat(phoneCanProtect(lowRam = true, sdk = sdk)).isTrue() }
        assertThat(phoneCanProtect(lowRam = false, sdk = Build.VERSION_CODES.Q)).isTrue()
    }

    private fun restricted(sdk: Int, packageSource: Int?): List<Access> =
        Access.entries.filter { mayBeRestricted(it, sdk, packageSource) }
}
