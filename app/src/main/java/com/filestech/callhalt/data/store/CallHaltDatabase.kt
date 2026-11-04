package com.filestech.callhalt.data.store

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The detector's memory and the journal, in ONE database so that they are written in one transaction
 * (docs/ARCHITECTURE.md, D-004). Not encrypted by SQLCipher, on purpose: Android's file-based encryption
 * already covers it, and a Keystore that fails one morning must not cost the protection (D-009).
 */
@Database(
    entities = [CallSessionEntity::class, PendingStopEntity::class, JournalEntryEntity::class],
    version = 2,
    exportSchema = true,
    // 1 → 2: the uptime columns (P-13), nullable, added as they are — CallHaltDatabaseMigrationTest.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class CallHaltDatabase : RoomDatabase() {

    abstract fun detectorDao(): DetectorDao

    companion object {
        const val NAME = "call_halt.db"
    }
}
