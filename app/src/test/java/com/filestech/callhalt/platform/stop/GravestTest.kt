package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.SensitiveActionKind.ACCESSIBILITY_ENABLED
import com.filestech.callhalt.domain.model.SensitiveActionKind.BANK_APP_OPENED
import com.filestech.callhalt.domain.model.SensitiveActionKind.BANK_VALIDATION_REQUEST
import com.filestech.callhalt.domain.model.SensitiveActionKind.CODE_MESSAGE
import com.filestech.callhalt.domain.model.SensitiveActionKind.REMOTE_CONTROL_INSTALLED
import com.filestech.callhalt.domain.model.SensitiveActionKind.REMOTE_CONTROL_OPENED
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/** Publication audit, N-1: what a STOP standing for several triggers speaks of. */
class GravestTest {

    /** The weight of each trigger, written out: a change of one is a choice, not a slip (review of 2026-11-07, 3). */
    private val rank = mapOf(
        ACCESSIBILITY_ENABLED to 2,
        REMOTE_CONTROL_OPENED to 1,
        REMOTE_CONTROL_INSTALLED to 1,
        CODE_MESSAGE to 0,
        BANK_VALIDATION_REQUEST to 0,
        BANK_APP_OPENED to 0,
    )

    @Test
    fun `every trigger has its weight`() {
        assertThat(rank.keys).containsExactlyElementsIn(SensitiveActionKind.entries)
        SensitiveActionKind.entries.forEach { kind -> assertWithMessage("$kind").that(gravity(kind)).isEqualTo(rank[kind]) }
    }

    /** Every ordered pair: the graver wins, and between equals the second, the newest. */
    @Test
    fun `of two, the graver, else the newer`() {
        SensitiveActionKind.entries.forEach { first ->
            SensitiveActionKind.entries.filter { it != first }.forEach { second ->
                val expected = if (rank.getValue(first) > rank.getValue(second)) first else second
                assertWithMessage("$first then $second").that(gravest(listOf(first, second))).isEqualTo(expected)
            }
        }
    }

    @Test
    fun `nothing to speak of is null`() {
        assertThat(gravest(emptyList())).isNull()
    }

    /** A trigger coming again is the newest: it moves to the end. */
    @Test
    fun `each trigger is kept at its last arrival`() {
        assertThat(inArrivalOrder(listOf(CODE_MESSAGE, REMOTE_CONTROL_INSTALLED, CODE_MESSAGE)))
            .containsExactly(REMOTE_CONTROL_INSTALLED, CODE_MESSAGE).inOrder()
        assertThat(inArrivalOrder(listOf(REMOTE_CONTROL_INSTALLED, REMOTE_CONTROL_OPENED, REMOTE_CONTROL_INSTALLED)))
            .containsExactly(REMOTE_CONTROL_OPENED, REMOTE_CONTROL_INSTALLED).inOrder()
    }
}
