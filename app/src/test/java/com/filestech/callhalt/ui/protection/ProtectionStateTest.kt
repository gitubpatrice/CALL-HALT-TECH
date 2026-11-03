package com.filestech.callhalt.ui.protection

import com.filestech.callhalt.platform.access.Access
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The activation asks for the first access missing: each one is of use only with the ones before it (D-022). */
class ProtectionStateTest {

    @Test
    fun `the missing accesses come in the order of the activation, whatever was granted first`() {
        assertThat(state(granted = emptySet()).missing)
            .containsExactly(Access.CALLS, Access.SCREEN, Access.BACKGROUND, Access.NOTIFICATIONS, Access.ALERTS).inOrder()
        // Notification access granted alone (the phone's settings, before the app was opened): the calls still come first.
        assertThat(state(granted = setOf(Access.NOTIFICATIONS)).missing)
            .containsExactly(Access.CALLS, Access.SCREEN, Access.BACKGROUND, Access.ALERTS).inOrder()
        assertThat(state(granted = setOf(Access.CALLS)).missing.first()).isEqualTo(Access.SCREEN)
    }

    /**
     * Samsung's deep sleep takes the notification access back, and turns it off again while the app is in it (measured on
     * the S24 FE, D-023): the way out of the deep sleep comes first, or the user would turn a switch that falls back.
     */
    @Test
    fun `out of the phone's sleep before the notification access`() {
        val asleep = state(granted = setOf(Access.CALLS, Access.SCREEN, Access.ALERTS))
        assertThat(asleep.missing).containsExactly(Access.BACKGROUND, Access.NOTIFICATIONS).inOrder()
    }

    @Test
    fun `nothing missing once every access is granted`() {
        assertThat(state(granted = Access.entries.toSet()).missing).isEmpty()
    }

    /** Storage failing (P-10): never "on", and said only once the accesses, which the user can grant here, are all there. */
    @Test
    fun `storage failing weakens the protection, the accesses first`() {
        assertThat(state(granted = Access.entries.toSet()).status).isEqualTo(ProtectionStatus.ON)
        assertThat(state(granted = Access.entries.toSet(), storageFailing = true).status)
            .isEqualTo(ProtectionStatus.STORAGE_FAILING)
        assertThat(state(granted = setOf(Access.CALLS), storageFailing = true).status).isEqualTo(ProtectionStatus.OFF)
        assertThat(state(granted = Access.entries.toSet(), storageFailing = true, phoneCanProtect = false).status)
            .isEqualTo(ProtectionStatus.UNSUPPORTED)
    }

    /**
     * D-024 (Patrice: "ok pour B"): without the STOP's fallback the screen still shows on most phones — weakened, not off.
     * Any other access missing is "off", whatever the fallback.
     */
    @Test
    fun `only the fallback missing weakens the protection, never turns it off`() {
        val allButAlerts = Access.entries.toSet() - Access.ALERTS
        assertThat(state(granted = allButAlerts).status).isEqualTo(ProtectionStatus.NO_ALERT)
        assertThat(state(granted = allButAlerts, storageFailing = true).status).isEqualTo(ProtectionStatus.NO_ALERT)
        assertThat(state(granted = allButAlerts - Access.CALLS).status).isEqualTo(ProtectionStatus.OFF)
    }

    /** D-025: on a Xiaomi, MIUI's autostart comes with the battery, before the notification access. */
    @Test
    fun `on a Xiaomi the autostart comes after the battery`() {
        val xiaomi = state(granted = emptySet(), applicable = Access.entries)
        assertThat(xiaomi.missing).containsExactlyElementsIn(Access.entries).inOrder()
        assertThat(Access.entries.indexOf(Access.AUTOSTART)).isEqualTo(Access.entries.indexOf(Access.BACKGROUND) + 1)
        // Elsewhere it does not apply: never missing, whatever the phone answers for it.
        assertThat(state(granted = Access.entries.toSet() - Access.AUTOSTART).status).isEqualTo(ProtectionStatus.ON)
    }

    /**
     * D-025: every access granted but the listener kept unbound (MIUI, measured) is "off", not "on" — and said after the
     * accesses still missing, before what only weakens the protection.
     */
    @Test
    fun `a listener kept unbound turns the protection off in fact`() {
        val all = Access.entries.toSet()
        assertThat(state(granted = all, listening = false).status).isEqualTo(ProtectionStatus.NOT_LISTENING)
        assertThat(state(granted = all - Access.ALERTS, listening = false).status).isEqualTo(ProtectionStatus.NOT_LISTENING)
        assertThat(state(granted = all - Access.CALLS, listening = false).status).isEqualTo(ProtectionStatus.OFF)
    }

    private fun state(
        granted: Set<Access>,
        storageFailing: Boolean = false,
        phoneCanProtect: Boolean = true,
        applicable: List<Access> = Access.entries - Access.AUTOSTART,
        listening: Boolean = true,
    ) = ProtectionState(
        granted,
        mayBeRestricted = emptySet(),
        afterCallMinutes = 15,
        phoneCanProtect,
        storageFailing,
        applicable,
        listening,
    )
}
