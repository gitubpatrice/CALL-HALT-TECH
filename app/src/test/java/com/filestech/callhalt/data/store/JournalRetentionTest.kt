package com.filestech.callhalt.data.store

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** D-029: the journal keeps 14 months — the 13 the law leaves to contest a payment from its debit, and one more. */
class JournalRetentionTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `fourteen months, counted on the calendar of the phone`() {
        val now = ZonedDateTime.of(2027, 1, 15, 9, 30, 0, 0, paris)

        assertThat(JournalRetention.cutoff(now)).isEqualTo(ZonedDateTime.of(2025, 11, 15, 9, 30, 0, 0, paris).toInstant())
    }

    /** A month shorter than the day: the last day of that month, never a day carried into the next one. */
    @Test
    fun `the end of a short month`() {
        val now = ZonedDateTime.of(2027, 4, 30, 12, 0, 0, 0, paris)

        assertThat(JournalRetention.cutoff(now)).isEqualTo(ZonedDateTime.of(2026, 2, 28, 12, 0, 0, 0, paris).toInstant())
    }

    /** Across a change of summer time, the same hour on the wall: a line written at 9:00 goes at 9:00. */
    @Test
    fun `the same hour across summer time`() {
        val now = ZonedDateTime.of(2027, 5, 15, 9, 0, 0, 0, paris) // Summer time: 7:00 UTC.

        // Winter time 14 months before: 9:00 in Paris is 8:00 UTC.
        assertThat(JournalRetention.cutoff(now)).isEqualTo(Instant.parse("2026-03-15T08:00:00Z"))
    }
}
