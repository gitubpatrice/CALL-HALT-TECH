package com.filestech.callhalt.data.store

import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class DetectorMappingTest {

    private val t0: Instant = Instant.parse("2026-11-01T10:00:00Z")

    private val fullState = DetectorState(
        calls = mapOf(
            CallChannel.CELLULAR to CallSession(
                CallChannel.CELLULAR,
                CallDirection.INCOMING,
                Counterpart.Number("+33612345678", inContacts = false),
                startedAt = t0,
                endedAt = t0.plusSeconds(600),
            ),
            CallChannel.VOIP to CallSession(
                CallChannel.VOIP,
                CallDirection.UNKNOWN,
                Counterpart.Unreadable,
                startedAt = t0.plusSeconds(60),
            ),
        ),
        pendingStops = mapOf(SensitiveActionKind.CODE_MESSAGE to t0.plusSeconds(120)),
        longCallWarned = setOf(CallChannel.CELLULAR),
    )

    private fun roundTrip(state: DetectorState) =
        DetectorMapping.toState(DetectorMapping.callRows(state), DetectorMapping.stopRows(state))

    @Test
    fun `a full state survives the round trip`() {
        assertThat(roundTrip(fullState)).isEqualTo(fullState)
    }

    @Test
    fun `the empty state survives the round trip`() {
        assertThat(roundTrip(DetectorState())).isEqualTo(DetectorState())
    }

    @Test
    fun `a hidden caller survives the round trip`() {
        val hidden = DetectorState(
            calls = mapOf(CallChannel.CELLULAR to CallSession(CallChannel.CELLULAR, CallDirection.OUTGOING, Counterpart.Hidden, t0)),
        )

        assertThat(roundTrip(hidden)).isEqualTo(hidden)
    }

    @Test
    fun `a row that no longer reads is dropped, the others kept`() {
        val rows = DetectorMapping.callRows(fullState)
        val unknownChannel = rows.first().copy(channel = "FAX")

        val state = DetectorMapping.toState(listOf(unknownChannel) + rows.drop(1), emptyList())

        assertThat(state.calls.keys).containsExactly(CallChannel.VOIP)
    }

    @Test
    fun `a row ending before it starts is dropped instead of crashing`() {
        val corrupted = DetectorMapping.callRows(fullState).first().let { it.copy(endedAt = it.startedAt - 1) }

        assertThat(DetectorMapping.toState(listOf(corrupted), emptyList()).calls).isEmpty()
    }

    @Test
    fun `a pending stop with an unknown trigger is dropped`() {
        val state = DetectorMapping.toState(emptyList(), listOf(PendingStopEntity("SOMETHING_ELSE", 0)))

        assertThat(state.pendingStops).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(SensitiveActionKind::class)
    fun `every STOP journal entry survives the round trip`(trigger: SensitiveActionKind) {
        val entry = JournalEntry.Stop(t0, trigger, source = "com.example.bank", counterpart = Counterpart.Hidden)

        assertThat(DetectorMapping.journalEntry(DetectorMapping.journalRow(entry))).isEqualTo(entry)
    }

    @ParameterizedTest
    @EnumSource(SensitiveActionKind::class)
    fun `every not-shown journal entry survives the round trip`(trigger: SensitiveActionKind) {
        val entry = JournalEntry.NotShown(t0, trigger)

        assertThat(DetectorMapping.journalEntry(DetectorMapping.journalRow(entry))).isEqualTo(entry)
    }

    @ParameterizedTest
    @EnumSource(WarningReason::class)
    fun `every warning journal entry survives the round trip`(reason: WarningReason) {
        val entry = JournalEntry.Warning(t0, reason)

        assertThat(DetectorMapping.journalEntry(DetectorMapping.journalRow(entry))).isEqualTo(entry)
    }

    @ParameterizedTest
    @EnumSource(StopChoice::class)
    fun `every answer journal entry survives the round trip`(choice: StopChoice) {
        val entry = JournalEntry.Answer(t0, SensitiveActionKind.BANK_VALIDATION_REQUEST, choice)

        assertThat(DetectorMapping.journalEntry(DetectorMapping.journalRow(entry))).isEqualTo(entry)
    }

    @Test
    fun `a journal number without its contact flag is dropped, not read as unknown`() {
        val entry = JournalEntry.Stop(
            t0,
            SensitiveActionKind.CODE_MESSAGE,
            source = null,
            counterpart = Counterpart.Number("+33612345678", inContacts = true),
        )
        val row = DetectorMapping.journalRow(entry).copy(counterpartInContacts = null)

        assertThat(DetectorMapping.journalEntry(row)).isNull()
    }

    @Test
    fun `a journal row of an unknown type is dropped`() {
        val row = DetectorMapping.journalRow(JournalEntry.Warning(t0, WarningReason.LONG_CALL_WITH_UNKNOWN)).copy(type = "NOTE")

        assertThat(DetectorMapping.journalEntry(row)).isNull()
    }
}
