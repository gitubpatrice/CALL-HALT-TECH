package com.filestech.callhalt.data.store

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

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

        /**
         * The database as the app opens it; the tests open it here too, so that they check what runs.
         *
         * Each commit synced to the disk before it returns ([SyncEachCommit], docs/ARCHITECTURE.md, D-027): a signal
         * the engine has decided on must survive a power cut — a battery that gives out, a forced restart. WAL off, so
         * that Android keeps one connection, the one [SyncEachCommit] sets. A few writes per call: the cost is nothing.
         *
         * No `fallbackToDestructiveMigration`, ever: a missing migration must never erase the journal a relative relies
         * on. It throws instead — caught by a migration test before any release, and at run time by RoomDetectorStore,
         * which then decides from memory; the home screen says the protection is weakened (P-10). Migrations are
         * additive only (D-004).
         */
        fun create(context: Context, name: String = NAME): CallHaltDatabase =
            Room.databaseBuilder(context, CallHaltDatabase::class.java, name)
                .setJournalMode(JournalMode.TRUNCATE)
                .addCallback(SyncEachCommit)
                .build()
    }
}

/**
 * A rollback journal, truncated, and every commit synced (`synchronous=FULL`), on every phone. Neither is Android's to
 * pick: its WAL syncs only at checkpoints (`NORMAL`), and a call committed seconds before a power cut was gone at the
 * next start (measured, docs/ARCHITECTURE.md, D-027); with WAL off, its defaults are the manufacturer's — `TRUNCATE`
 * and `FULL` on Android's own images, `PERSIST` and `NORMAL` on a Samsung S9 (measured). The same pair Room sets when
 * it opens SQLite through a driver (its `RoomConnectionManager`). Set on the one connection Android keeps without WAL,
 * at each open. With FULL, SQLite also syncs the truncated journal (pager.c, since 2014): the commit is on the disk
 * when the transaction returns — a cut 1 s after it kept the call (measured).
 */
internal object SyncEachCommit : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        // A query, which execSQL refuses: it returns the mode now in force. Run only once the cursor is read.
        val mode = db.query("PRAGMA journal_mode = TRUNCATE").use { if (it.moveToFirst()) it.getString(0) else null }
        if (mode != "truncate") Timber.w("Journal mode left at %s", mode)
        db.execSQL("PRAGMA synchronous = FULL")
    }
}
