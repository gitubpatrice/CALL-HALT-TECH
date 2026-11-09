package com.filestech.callhalt.domain.history

import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.SensitiveActionKind.BANK_VALIDATION_REQUEST
import com.filestech.callhalt.domain.model.SensitiveActionKind.CODE_MESSAGE
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The journal read back as alerts, each STOP with its answer (function 8, audit #1, D-041). */
class AlertTest {

    private fun at(minute: Long): Instant = Instant.parse("2026-11-09T10:00:00Z").plusSeconds(minute * 60)

    private fun stop(minute: Long, trigger: com.filestech.callhalt.domain.model.SensitiveActionKind = CODE_MESSAGE) =
        JournalEntry.Stop(at(minute), trigger, "com.google.android.apps.messaging", Counterpart.Unreadable)

    @Test
    fun `each STOP carries its answer, the newest first`() {
        val history = alerts(
            listOf(
                stop(0),
                JournalEntry.Answer(at(1), CODE_MESSAGE, StopChoice.HANG_UP),
                stop(5, BANK_VALIDATION_REQUEST),
                JournalEntry.Answer(at(6), BANK_VALIDATION_REQUEST, StopChoice.CALL_MY_BANK),
            ),
        )

        assertThat(history).containsExactly(
            Alert.Stop(at(5), BANK_VALIDATION_REQUEST, "com.google.android.apps.messaging", listOf(StopChoice.CALL_MY_BANK)),
            Alert.Stop(at(0), CODE_MESSAGE, "com.google.android.apps.messaging", listOf(StopChoice.HANG_UP)),
        ).inOrder()
    }

    /** FN-4: left, back once, then answered — the two answers, in order. */
    @Test
    fun `a screen left and back keeps both answers`() {
        val history = alerts(
            listOf(
                stop(0),
                JournalEntry.Answer(at(1), CODE_MESSAGE, StopChoice.LEFT),
                JournalEntry.Answer(at(2), CODE_MESSAGE, StopChoice.HANG_UP),
            ),
        )

        assertThat((history.single() as Alert.Stop).answers).containsExactly(StopChoice.LEFT, StopChoice.HANG_UP).inOrder()
    }

    /** The answer of a trigger goes to that trigger's latest STOP, not to another one in between. */
    @Test
    fun `answers go to the latest STOP of their trigger`() {
        val history = alerts(
            listOf(
                stop(0),
                stop(1, BANK_VALIDATION_REQUEST),
                JournalEntry.Answer(at(2), CODE_MESSAGE, StopChoice.CONTINUE),
                stop(3),
            ),
        )

        assertThat(history.map { (it as Alert.Stop).answers }).containsExactly(
            emptyList<StopChoice>(),
            emptyList<StopChoice>(),
            listOf(StopChoice.CONTINUE),
        ).inOrder()
    }

    @Test
    fun `a STOP the phone did not show says so`() {
        val history = alerts(listOf(stop(0), JournalEntry.NotShown(at(0), CODE_MESSAGE)))

        assertThat((history.single() as Alert.Stop).notShown).isTrue()
    }

    /** FN-4: seen, left, then its return blocked by the phone — this screen was shown (review of 2026-11-09, 3). */
    @Test
    fun `a return not shown does not make the screen seen read as never shown`() {
        val history = alerts(
            listOf(stop(0), JournalEntry.Answer(at(1), CODE_MESSAGE, StopChoice.LEFT), JournalEntry.NotShown(at(2), CODE_MESSAGE)),
        )

        val stop = history.single() as Alert.Stop
        assertThat(stop.notShown).isFalse()
        assertThat(stop.answers).containsExactly(StopChoice.LEFT)
    }

    /**
     * Read in the order written, never by time: a civil clock set back between a STOP and its answer gives the answer an
     * earlier time; it is still that STOP's (review of 2026-11-09, 1).
     */
    @Test
    fun `an answer stamped before its STOP by a clock set back is still its own`() {
        val history = alerts(
            listOf(
                stop(0),
                stop(10),
                JournalEntry.Answer(at(9), CODE_MESSAGE, StopChoice.HANG_UP),
            ),
        )

        assertThat(history.map { (it as Alert.Stop).answers })
            .containsExactly(listOf(StopChoice.HANG_UP), emptyList<StopChoice>())
            .inOrder()
    }

    /** One screen standing for two triggers: one answer per trigger, each to its own STOP. */
    @Test
    fun `a screen that stood for two triggers gives each STOP its answer`() {
        val history = alerts(
            listOf(
                stop(0),
                stop(1, BANK_VALIDATION_REQUEST),
                JournalEntry.Answer(at(2), CODE_MESSAGE, StopChoice.HANG_UP),
                JournalEntry.Answer(at(2), BANK_VALIDATION_REQUEST, StopChoice.HANG_UP),
            ),
        )

        val answers = history.map { (it as Alert.Stop).answers }
        assertThat(answers).containsExactly(listOf(StopChoice.HANG_UP), listOf(StopChoice.HANG_UP))
    }

    /** Its STOP was older than the lines kept (14 months): nothing to attach it to. */
    @Test
    fun `an answer without its STOP is left out`() {
        assertThat(alerts(listOf(JournalEntry.Answer(at(0), CODE_MESSAGE, StopChoice.HANG_UP)))).isEmpty()
    }

    @Test
    fun `a warning is an alert of its own`() {
        val warning = JournalEntry.Warning(at(0), WarningReason.REMOTE_CONTROL_INSTALLED, "com.anydesk.anydeskandroid")

        assertThat(alerts(listOf(warning)))
            .containsExactly(Alert.Warning(at(0), WarningReason.REMOTE_CONTROL_INSTALLED, "com.anydesk.anydeskandroid"))
    }
}
