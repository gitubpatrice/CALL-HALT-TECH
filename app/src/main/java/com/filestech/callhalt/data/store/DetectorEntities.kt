package com.filestech.callhalt.data.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema of version 1. Times are epoch milliseconds (UTC); enums are stored by NAME, never by ordinal, so
 * that reordering an enum cannot silently turn one value into another. Until a first APK is published,
 * version 1 may still change; from then on, migrations are additive only (docs/ARCHITECTURE.md, D-004).
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
)

/** A STOP screen shown and not answered yet. */
@Entity(tableName = "pending_stop")
data class PendingStopEntity(
    @PrimaryKey val trigger: String,
    @ColumnInfo(name = "shown_at") val shownAt: Long,
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
