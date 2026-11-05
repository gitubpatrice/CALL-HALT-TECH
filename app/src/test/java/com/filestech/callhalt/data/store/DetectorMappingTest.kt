package com.filestech.callhalt.data.store

import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.Uptime
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.Instant

class DetectorMappingTest {

    private val t0: Instant = Instant.parse("2026-11-01T10:00:00Z")

    /** [seconds] after t0, 90 minutes after boot 5 — on both clocks (P-13). */
    private fun moment(seconds: Long) = Moment(t0.plusSeconds(seconds), Uptime(5, Duration.ofMinutes(90).plusSeconds(seconds)))

    private val fullState = DetectorState(
        calls = mapOf(
            CallChannel.CELLULAR to CallSession(
                CallChannel.CELLULAR,
                CallDirection.INCOMING,
                Counterpart.Number("+33612345678", inContacts = false),
                startedAt = moment(0),
                // Carried over at a restart: in the next boot's time, before it began (DetectionEngine).
                endedAt = Moment(t0.plusSeconds(600), Uptime(6, Duration.ofSeconds(-30))),
            ),
            // Written before the uptime was kept (schema 1): the civil time alone.
            CallChannel.VOIP to CallSession(
                CallChannel.VOIP,
                CallDirection.UNKNOWN,
                Counterpart.Unreadable,
                startedAt = Moment(t0.plusSeconds(60)),
            ),
        ),
        pendingStops = mapOf(SensitiveActionKind.CODE_MESSAGE to moment(120)),
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
            calls = mapOf(CallChannel.CELLULAR to CallSession(CallChannel.CELLULAR, CallDirection.OUTGOING, Counterpart.Hidden, moment(0))),
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

    /** Before its start on the clock that decides: the uptime within one boot, the civil time without it. */
    @Test
    fun `a row ending before it starts is dropped instead of crashing`() {
        val sameBoot = CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden, moment(0), moment(60))
        val row = DetectorMapping.callRows(DetectorState(calls = mapOf(CallChannel.CELLULAR to sameBoot))).single()
        val onTheUptime = row.copy(endedSinceBoot = checkNotNull(row.startedSinceBoot) - 1)
        val onTheCivilClock = row.copy(endedAt = row.startedAt - 1, startedBoot = null, endedBoot = null)

        assertThat(DetectorMapping.toState(listOf(onTheUptime), emptyList()).calls).isEmpty()
        assertThat(DetectorMapping.toState(listOf(onTheCivilClock), emptyList()).calls).isEmpty()
        // The civil clock set back during the call: an end "before" the start on it alone is still the call's.
        assertThat(DetectorMapping.toState(listOf(row.copy(endedAt = row.startedAt - 1)), emptyList()).calls).hasSize(1)
    }

    @Test
    fun `an uptime with one of its two columns missing reads as none`() {
        val row = DetectorMapping.stopRows(fullState).single().copy(shownSinceBoot = null)

        assertThat(DetectorMapping.toState(emptyList(), listOf(row)).pendingStops.values.single())
            .isEqualTo(Moment(t0.plusSeconds(120)))
    }

    @Test
    fun `a pending stop with an unknown trigger is dropped`() {
        val state = DetectorMapping.toState(emptyList(), listOf(PendingStopEntity("SOMETHING_ELSE", 0, null, null)))

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

    /** The app a warning was about: in the column a STOP's source already has, no new one. */
    @Test
    fun `a warning keeps the app it was about`() {
        val entry = JournalEntry.Warning(t0, WarningReason.REMOTE_CONTROL_INSTALLED, source = "com.anydesk.anydeskandroid")

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
