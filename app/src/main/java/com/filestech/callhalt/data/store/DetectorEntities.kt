package com.filestech.callhalt.data.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema of version 2. Times are epoch milliseconds (UTC); enums are stored by NAME, never by ordinal, so
 * that reordering an enum cannot silently turn one value into another. Migrations are additive only
 * (docs/ARCHITECTURE.md, D-004): version 2 added the uptime beside each time the engine decides on — the boot
 * and the milliseconds since it, null in the rows written before (P-13). The journal keeps the civil time alone.
 */

/** The latest call of one channel (one row per channel), in progress or ended. */
@Entity(tableName = "call_session")
data class CallSessionEntity(
    @PrimaryKey val channel: String,
    val direction: String,
    @ColumnInfo(name = "counterpart_kind") val counterpartKind: String,
    @ColumnInfo(name = "counterpart_number") val counterpartNumber: String?,
    @ColumnInfo(name = "counterpart_in_contacts") val counterpartInContacts: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "long_call_warned") val longCallWarned: Boolean,
    @ColumnInfo(name = "started_boot") val startedBoot: Int?,
    @ColumnInfo(name = "started_since_boot") val startedSinceBoot: Long?,
    @ColumnInfo(name = "ended_boot") val endedBoot: Int?,
    @ColumnInfo(name = "ended_since_boot") val endedSinceBoot: Long?,
)

/** A STOP screen shown and not answered yet. */
@Entity(tableName = "pending_stop")
data class PendingStopEntity(
    @PrimaryKey val trigger: String,
    @ColumnInfo(name = "shown_at") val shownAt: Long,
    @ColumnInfo(name = "shown_boot") val shownBoot: Int?,
    @ColumnInfo(name = "shown_since_boot") val shownSinceBoot: Long?,
)

/** One line of the journal (PROJET.md, § 4.8). [type] says which of the optional columns are set. */
@Entity(tableName = "journal_entry", indices = [Index("at")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,
    val trigger: String?,
    val warning: String?,
    val choice: String?,
    val source: String?,
    @ColumnInfo(name = "counterpart_kind") val counterpartKind: String?,
    @ColumnInfo(name = "counterpart_number") val counterpartNumber: String?,
    @ColumnInfo(name = "counterpart_in_contacts") val counterpartInContacts: Boolean?,
)
