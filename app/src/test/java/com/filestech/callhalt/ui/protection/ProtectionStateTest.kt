package com.filestech.callhalt.ui.protection

import com.filestech.callhalt.platform.access.Access
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The activation asks for the first access missing: each one is of use only with the ones before it (D-022). */
class ProtectionStateTest {

    @Test
    fun `the missing accesses come in the order of the activation, whatever was granted first`() {
        assertThat(state(granted = emptySet()).missing)
            .containsExactly(Access.CALLS, Access.SCREEN, Access.BACKGROUND, Access.NOTIFICATIONS).inOrder()
        // Notification access granted alone (the phone's settings, before the app was opened): the calls still come first.
        assertThat(state(granted = setOf(Access.NOTIFICATIONS)).missing)
            .containsExactly(Access.CALLS, Access.SCREEN, Access.BACKGROUND).inOrder()
        assertThat(state(granted = setOf(Access.CALLS)).missing.first()).isEqualTo(Access.SCREEN)
    }

    /**
     * Samsung's deep sleep takes the notification access back, and turns it off again while the app is in it (measured on
     * the S24 FE, D-023): the way out of the deep sleep comes first, or the user would turn a switch that falls back.
     */
    @Test
    fun `out of the phone's sleep before the notification access`() {
        val asleep = state(granted = setOf(Access.CALLS, Access.SCREEN))
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
        assertThat(state(granted = Access.entries.toSet(), storageFailing = true).status).isEqualTo(ProtectionStatus.WEAKENED)
        assertThat(state(granted = setOf(Access.CALLS), storageFailing = true).status).isEqualTo(ProtectionStatus.OFF)
        assertThat(state(granted = Access.entries.toSet(), storageFailing = true, phoneCanProtect = false).status)
            .isEqualTo(ProtectionStatus.UNSUPPORTED)
    }

    private fun state(granted: Set<Access>, storageFailing: Boolean = false, phoneCanProtect: Boolean = true) =
        ProtectionState(granted, mayBeRestricted = emptySet(), afterCallMinutes = 15, phoneCanProtect, storageFailing)
}
