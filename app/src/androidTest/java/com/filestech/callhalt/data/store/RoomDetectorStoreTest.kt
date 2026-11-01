package com.filestech.callhalt.data.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Signal
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * On a real SQLite: the property the whole method rests on (docs/ARCHITECTURE.md, D-004) — the call
 * recorded by one process is still there for the SMS that arrives in the next one.
 */
@RunWith(AndroidJUnit4::class)
class RoomDetectorStoreTest {

    private lateinit var database: CallHaltDatabase
    private val engine = DetectionEngine()
    private val t0: Instant = Instant.parse("2026-11-01T10:00:00Z")

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CallHaltDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun RoomDetectorStore.process(signal: Signal): List<Decision> =
        apply { state -> engine.process(state, signal, ProtectionSettings()) }

    @Test
    fun theCallSurvivesTheProcessThatRecordedIt() = runTest {
        RoomDetectorStore(database).process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))

        // A NEW store: nothing in memory, as after Android killed the process between the two signals.
        val decisions = RoomDetectorStore(database).process(SensitiveAction(t0.plusSeconds(120), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }

    @Test
    fun theJournalIsWrittenWithTheState() = runTest {
        val store = RoomDetectorStore(database)
        store.process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        store.process(SensitiveAction(t0.plusSeconds(120), SensitiveActionKind.BANK_VALIDATION_REQUEST, source = "com.example.bank"))

        val journal = database.detectorDao().journal().first().mapNotNull(DetectorMapping::journalEntry)

        assertThat(journal).containsExactly(
            JournalEntry.Stop(t0.plusSeconds(120), SensitiveActionKind.BANK_VALIDATION_REQUEST, "com.example.bank", Counterpart.Hidden),
        )
    }

    @Test
    fun aClosedDatabaseStillYieldsTheDecisions() = runTest {
        val store = RoomDetectorStore(database)
        store.process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        database.close()

        // Storage gone: the STOP must still be decided, from the state this process committed last.
        val decisions = store.process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }

    /** A disk that refuses writes into [table], as SQLite reports a full disk: the transaction aborts. */
    private fun failWritesInto(table: String) {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER full_$table BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'disk full'); END",
        )
    }

    @Test
    fun aWriteThatFailsAfterTheReadKeepsTheDecision() = runTest {
        RoomDetectorStore(database).process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        failWritesInto("journal_entry")

        // A NEW process: nothing in memory. The call is read and the STOP decided, then the journal write fails.
        val decisions = RoomDetectorStore(database).process(SensitiveAction(t0.plusSeconds(120), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }

    @Test
    fun theStateStorageFailedToKeepIsTheTruthUntilWrittenBack() = runTest {
        failWritesInto("call_session")
        val store = RoomDetectorStore(database)
        store.process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER full_call_session")

        // Storage is back but never got the call: the state in memory must decide, then be written back.
        val decisions = store.process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        assertThat(database.detectorDao().callSessions()).hasSize(1)
    }

    /**
     * The KNOWN LIMIT, written down so that it cannot be mistaken for a guarantee (docs/ARCHITECTURE.md, P-7):
     * a process that starts after the storage failed knows no call, and misses the STOP. When the telephony
     * adapter brings a live probe of the call state, this test changes side.
     */
    @Test
    fun aProcessStartedAfterTheFailureKnowsNoCall_theKnownLimit() = runTest {
        RoomDetectorStore(database).process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        database.close()

        val decisions = RoomDetectorStore(database).process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions).isEmpty()
    }
}
