package com.filestech.callhalt.data.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each commit synced to the disk, the way the app opens its database (docs/ARCHITECTURE.md, D-027): with WAL, Android
 * syncs only at checkpoints, and a power cut undid the last signals. Read from SQLite itself, on the phone the test runs
 * on — without WAL, the defaults are the manufacturer's.
 */
@RunWith(AndroidJUnit4::class)
class CallHaltDatabaseJournalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CallHaltDatabase

    @Before
    fun open() {
        context.deleteDatabase(NAME)
        database = CallHaltDatabase.create(context, NAME)
    }

    @After
    fun close() {
        database.close()
        context.deleteDatabase(NAME)
    }

    /** On a Samsung S9 this fails without [SyncEachCommit]: `persist` and NORMAL (measured). */
    @Test
    fun eachCommitIsSyncedToTheDisk() {
        assertThat(pragma("journal_mode")).isEqualTo("truncate")
        assertThat(pragma("synchronous")).isEqualTo(FULL)
    }

    /** The S9's defaults, set back on the connection: on Android's own images, which already sync, this is the check. */
    @Test
    fun aManufacturersDefaultsAreOverridden() {
        pragma("journal_mode = PERSIST")
        database.openHelper.writableDatabase.execSQL("PRAGMA synchronous = NORMAL")
        assertThat(pragma("journal_mode")).isEqualTo("persist")
        assertThat(pragma("synchronous")).isEqualTo(NORMAL)

        SyncEachCommit.onOpen(database.openHelper.writableDatabase)

        assertThat(pragma("journal_mode")).isEqualTo("truncate")
        assertThat(pragma("synchronous")).isEqualTo(FULL)
    }

    private fun pragma(statement: String): String =
        database.openHelper.writableDatabase.query("PRAGMA $statement").use { cursor ->
            check(cursor.moveToFirst()) { "PRAGMA $statement returned nothing" }
            cursor.getString(0)
        }

    private companion object {
        const val NAME = "journal-test.db"
        const val NORMAL = "1"
        const val FULL = "2"
    }
}
