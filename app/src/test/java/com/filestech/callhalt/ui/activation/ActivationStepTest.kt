package com.filestech.callhalt.ui.activation

import com.filestech.callhalt.platform.access.Access
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** "Step x of y": the steps of this activation, not every access of the phone (measured on Android 11, 2026-11-04). */
class ActivationStepTest {

    private val phone = Access.entries - Access.AUTOSTART

    /** Before Android 13 the alert is granted by default: four steps, and the last one says so. */
    @Test
    fun `an access granted already is no step`() {
        val missing = phone - Access.ALERTS
        assertThat(activationStep(Access.CALLS, emptyList(), missing, phone)).isEqualTo(1 to 4)
        val before = listOf(Access.CALLS, Access.SCREEN, Access.BACKGROUND)
        assertThat(activationStep(Access.NOTIFICATIONS, before, listOf(Access.NOTIFICATIONS), phone)).isEqualTo(4 to 4)
    }

    /** Granted in the settings before the activation (the notification access): the count never jumps. */
    @Test
    fun `the steps follow one another, whatever was granted before`() {
        val missing = phone - Access.NOTIFICATIONS
        val shown = mutableListOf<Access>()
        val counts = missing.map { current ->
            shown += current
            activationStep(current, shown, missing.dropWhile { it != current }, phone)
        }
        assertThat(counts).containsExactly(1 to 4, 2 to 4, 3 to 4, 4 to 4).inOrder()
    }

    /** The step on screen, counted once: its own entry in [shown] is not counted twice. */
    @Test
    fun `the current step is counted once`() {
        assertThat(activationStep(Access.SCREEN, listOf(Access.CALLS, Access.SCREEN), phone - Access.CALLS, phone)).isEqualTo(2 to 5)
    }

    /** A step passed (the battery, refused for good) stays counted; the next one comes after it. */
    @Test
    fun `a step passed stays counted`() {
        val missing = listOf(Access.BACKGROUND, Access.NOTIFICATIONS, Access.ALERTS)
        assertThat(activationStep(Access.NOTIFICATIONS, listOf(Access.CALLS, Access.SCREEN, Access.BACKGROUND), missing, phone))
            .isEqualTo(4 to 5)
    }

    /** A Xiaomi has the autostart (D-025): six steps, one after the other. */
    @Test
    fun `a Xiaomi counts its autostart`() {
        val xiaomi = Access.entries
        val shown = mutableListOf<Access>()
        val counts = xiaomi.map { current ->
            shown += current
            activationStep(current, shown, xiaomi.dropWhile { it != current }, xiaomi)
        }
        assertThat(counts).containsExactly(1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6, 6 to 6).inOrder()
    }

    /**
     * The display over the others taken back while the notification access is asked: the step goes back to it, and the
     * notification access, shown already, is not counted twice.
     */
    @Test
    fun `an access taken back during the activation is counted once`() {
        val shown = listOf(Access.CALLS, Access.SCREEN, Access.BACKGROUND, Access.NOTIFICATIONS)
        val missing = listOf(Access.SCREEN, Access.NOTIFICATIONS, Access.ALERTS)
        assertThat(activationStep(Access.SCREEN, shown, missing, phone)).isEqualTo(4 to 5)
    }

    /** No display over the others on an Android Go phone from 11 to 13 (D-030): not one of its steps. */
    @Test
    fun `what does not apply is no step`() {
        val go = phone - Access.SCREEN
        assertThat(activationStep(Access.CALLS, emptyList(), go, go)).isEqualTo(1 to 4)
    }
}
