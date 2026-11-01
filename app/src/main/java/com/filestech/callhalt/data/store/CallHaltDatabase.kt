package com.filestech.callhalt.data.store

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The detector's memory and the journal, in ONE database so that they are written in one transaction
 * (docs/ARCHITECTURE.md, D-004). Not encrypted by SQLCipher, on purpose: Android's file-based encryption
 * already covers it, and a Keystore that fails one morning must not cost the protection (D-009).
 */
@Database(
    entities = [CallSessionEntity::class, PendingStopEntity::class, JournalEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CallHaltDatabase : RoomDatabase() {

    abstract fun detectorDao(): DetectorDao

    companion object {
        const val NAME = "call_halt.db"
    }
}
