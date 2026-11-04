package com.filestech.callhalt.data.store

import java.time.Instant
import java.time.ZonedDateTime

/**
 * How long the journal keeps a line (docs/ARCHITECTURE.md, D-029), and how many it keeps at most. Pruned in the
 * transaction of every signal and of every check of the home screen ([RoomDetectorStore]): a line past its time goes
 * at the latest with the next call.
 */
internal object JournalRetention {

    /**
     * Fourteen months. The journal proves that a call was in progress when a payment was validated, what a bank asks of
     * a customer who contests it; the law leaves them 13 months from the DEBIT to do so (Code monétaire et financier,
     * art. L133-24). A card debited at the end of the month is debited up to a month after its validation: one month
     * more. Kept shorter, the proof could be gone while the claim is still open; longer, it serves nothing.
     */
    const val MONTHS = 14L

    /**
     * A bound on the lines, whatever their dates: an app that imitates a bank's channel during a call adds two lines per
     * STOP (the STOP and its answer), a STOP a minute if left unanswered, for as long as the call lasts (security review
     * of 2026-11-02). Normal use writes a few lines a month; 10 000 is, by estimate, a hundred years of it and about a
     * megabyte. The lines written LAST are kept, in the order of writing, whatever the civil clock said.
     */
    const val MAX_LINES = 10_000

    /** Lines older than this go: [MONTHS] months before [now], in the phone's time zone (the end of a short month kept). */
    fun cutoff(now: ZonedDateTime): Instant = now.minusMonths(MONTHS).toInstant()
}
