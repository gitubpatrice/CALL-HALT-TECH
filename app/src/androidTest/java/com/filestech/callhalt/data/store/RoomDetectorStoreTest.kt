package com.filestech.callhalt.data.store

import android.content.Context
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
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.model.Uptime
import com.filestech.callhalt.domain.model.WarningReason
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * On a real SQLite: the property the whole method rests on (docs/ARCHITECTURE.md, D-004) — the call
 * recorded by one process is still there for the SMS that arrives in the next one.
 */
@RunWith(AndroidJUnit4::class)
class RoomDetectorStoreTest {

    private lateinit var database: CallHaltDatabase
    private val engine = DetectionEngine()

    /** On both clocks: the uptime columns go through the real database too (P-13). */
    private val t0 = Moment(Instant.parse("2026-11-01T10:00:00Z"), Uptime(4, Duration.ofHours(1)))

    private fun Moment.plusSeconds(seconds: Long) = plus(Duration.ofSeconds(seconds))

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

    /** The civil time the store prunes the journal by ([JournalRetention]): the test's own, not the day it runs. */
    private fun clockAt(now: Instant): Clock = Clock.fixed(now, ZoneOffset.UTC)

    /** A store as a new process opens it, its retention clock at [now]. */
    private fun newStore(now: Instant = t0.wall) = RoomDetectorStore(database, clockAt(now))

    private suspend fun writeJournalLines(vararg at: Instant) = database.detectorDao().insertJournal(
        at.map { DetectorMapping.journalRow(JournalEntry.Warning(it, WarningReason.LONG_CALL_WITH_UNKNOWN)) },
    )

    private suspend fun journalTimes(): List<Instant> =
        database.detectorDao().journal().first().map { Instant.ofEpochMilli(it.at) }

    /**
     * D-029: 14 months, pruned by the signal that writes — a line past its time goes with the next call — and by the check
     * of the home screen, which writes no line. A line exactly 14 months old is kept.
     */
    @Test
    fun theJournalKeepsFourteenMonths() = runTest {
        val now = t0.wall
        val limit = Instant.parse("2025-09-01T10:00:00Z") // 14 months before 2026-11-01, 10:00 (UTC).
        writeJournalLines(limit.minusMillis(1), limit, now.minus(Duration.ofDays(30)))

        newStore(now).process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        assertThat(journalTimes()).containsExactly(now.minus(Duration.ofDays(30)), limit)

        // A day later, no signal: the home screen's check prunes what has just passed its time.
        newStore(now.plus(Duration.ofDays(1))).check()
        assertThat(journalTimes()).containsExactly(now.minus(Duration.ofDays(30)))
    }

    /**
     * D-029: whatever their dates, the lines written LAST are kept, up to the bound — here the last one stamped a year
     * EARLIER than the others, a civil clock set back: it is kept, and the first one written goes.
     */
    @Test
    fun theJournalKeepsItsLastLinesWhateverTheClock() = runTest {
        val now = t0.wall
        val firsts = List(JournalRetention.MAX_LINES) { now.minusSeconds(it.toLong()) }
        writeJournalLines(*firsts.toTypedArray())
        val setBack = now.minus(Duration.ofDays(365))
        writeJournalLines(setBack)

        newStore(now).check()

        val kept = journalTimes()
        assertThat(kept).hasSize(JournalRetention.MAX_LINES)
        assertThat(kept).contains(setBack)
        assertThat(kept).doesNotContain(firsts.first())
    }

    /** A clock set back, far: nothing is older than its cutoff, nothing goes. */
    @Test
    fun aClockSetBackDeletesNothing() = runTest {
        writeJournalLines(t0.wall)

        newStore(Instant.EPOCH).check()

        assertThat(journalTimes()).containsExactly(t0.wall)
    }

    @Test
    fun theCallSurvivesTheProcessThatRecordedIt() = runTest {
        newStore().process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))

        // A NEW store: nothing in memory, as after Android killed the process between the two signals.
        val decisions = newStore().process(SensitiveAction(t0.plusSeconds(120), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }

    @Test
    fun theJournalIsWrittenWithTheState() = runTest {
        val store = newStore()
        store.process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        store.process(SensitiveAction(t0.plusSeconds(120), SensitiveActionKind.BANK_VALIDATION_REQUEST, source = "com.example.bank"))

        val journal = database.detectorDao().journal().first().mapNotNull(DetectorMapping::journalEntry)

        assertThat(journal).containsExactly(
            JournalEntry.Stop(
                t0.plusSeconds(120).wall,
                SensitiveActionKind.BANK_VALIDATION_REQUEST,
                "com.example.bank",
                Counterpart.Hidden,
            ),
        )
    }

    @Test
    fun aClosedDatabaseStillYieldsTheDecisions() = runTest {
        val store = newStore()
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
        newStore().process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        failWritesInto("journal_entry")

        // A NEW process: nothing in memory. The call is read and the STOP decided, then the journal write fails.
        val decisions = newStore().process(SensitiveAction(t0.plusSeconds(120), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
    }

    @Test
    fun theStateStorageFailedToKeepIsTheTruthUntilWrittenBack() = runTest {
        failWritesInto("call_session")
        val store = newStore()
        store.process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER full_call_session")

        // Storage is back but never got the call: the state in memory must decide, then be written back.
        val decisions = store.process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
        assertThat(database.detectorDao().callSessions()).hasSize(1)
    }

    /** P-10: the home screen says the protection is weakened while storage fails, and no longer once it is repaired. */
    @Test
    fun aFailureShowsUntilTheCheckWritesTheStateBack() = runTest {
        failWritesInto("call_session")
        val store = newStore()
        store.process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        assertThat(store.failing.value).isTrue()

        store.check()
        assertThat(store.failing.value).isTrue() // Still full: still said.

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER full_call_session")
        store.check()

        // The user freed some space and came back: no signal needed, the call is kept and the warning gone.
        assertThat(store.failing.value).isFalse()
        assertThat(database.detectorDao().callSessions()).hasSize(1)
    }

    /**
     * A NEW process, nothing failed in it yet: a storage that still reads but no longer writes shows before any signal
     * (review of 2026-11-03 — a read alone would pass), and the call it holds is not lost by the check.
     */
    @Test
    fun theCheckWritesNotOnlyReads() = runTest {
        newStore().process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        failWritesInto("call_session")

        val store = newStore()
        store.check()

        assertThat(store.failing.value).isTrue()
        assertThat(database.detectorDao().callSessions()).hasSize(1)
    }

    /** A database that cannot be opened (a migration missing) shows before any signal, and a healthy one does not. */
    @Test
    fun theCheckOpensTheDatabase() = runTest {
        val healthy = newStore()
        healthy.check()
        assertThat(healthy.failing.value).isFalse()

        // A folder where the file should be: SQLite cannot open it — for the store, the same as a migration missing.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blocked = File(context.cacheDir, "blocked.db").apply { mkdirs() }
        val unopenable = CallHaltDatabase.create(context, blocked.path)
        try {
            val broken = RoomDetectorStore(unopenable, clockAt(t0.wall))
            broken.check()
            assertThat(broken.failing.value).isTrue()
        } finally {
            unopenable.close()
            blocked.delete()
        }
    }

    /**
     * The KNOWN LIMIT, written down so that it cannot be mistaken for a guarantee (docs/ARCHITECTURE.md, P-7):
     * a process that starts after the storage failed knows no call, and misses the STOP. When the telephony
     * adapter brings a live probe of the call state, this test changes side.
     */
    @Test
    fun aProcessStartedAfterTheFailureKnowsNoCall_theKnownLimit() = runTest {
        newStore().process(CallStarted(t0, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden))
        database.close()

        val decisions = newStore().process(SensitiveAction(t0.plusSeconds(60), SensitiveActionKind.CODE_MESSAGE))

        assertThat(decisions).isEmpty()
    }
}
