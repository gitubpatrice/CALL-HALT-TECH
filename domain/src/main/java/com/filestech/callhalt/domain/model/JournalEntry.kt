package com.filestech.callhalt.domain.model

import java.time.Instant

/**
 * What the journal keeps (PROJET.md, § 4.8): what happened, when, and what the user chose — readable on
 * the phone by the relative who looks after its owner. Read by people, it keeps the civil time of each
 * signal ([Moment.wall]), not the uptime the engine decides on.
 */
sealed interface JournalEntry {
    val at: Instant

    data class Stop(
        override val at: Instant,
        val trigger: SensitiveActionKind,
        val source: String?,
        val counterpart: Counterpart,
    ) : JournalEntry

    /** A STOP decided at [at] that could not be shown (see [com.filestech.callhalt.domain.model.StopNotShown]). */
    data class NotShown(override val at: Instant, val trigger: SensitiveActionKind) : JournalEntry

    data class Warning(override val at: Instant, val reason: WarningReason) : JournalEntry

    data class Answer(
        override val at: Instant,
        val trigger: SensitiveActionKind,
        val choice: StopChoice,
    ) : JournalEntry
}
