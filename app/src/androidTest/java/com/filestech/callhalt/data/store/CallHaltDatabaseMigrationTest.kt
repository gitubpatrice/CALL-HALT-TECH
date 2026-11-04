package com.filestech.callhalt.data.store

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Uptime
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * The migrations, replayed from the committed schemas (app/schemas): 1 → 2 adds the uptime beside each time the engine
 * decides on (docs/ARCHITECTURE.md, P-13). An update of the app in the middle of a call must keep the call: its rows,
 * written without an uptime, read on the civil clock, as before.
 */
@RunWith(AndroidJUnit4::class)
class CallHaltDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CallHaltDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theRowsOfVersion1SurviveOnTheCivilClock() = runTest {
        writeVersion1()
        helper.runMigrationsAndValidate(NAME, 2, true).close()

        val database = Room.databaseBuilder(context, CallHaltDatabase::class.java, NAME).build()
        try {
            val dao = database.detectorDao()
            val state = DetectorMapping.toState(dao.callSessions(), dao.pendingStops())

            val call = state.calls.getValue(CallChannel.CELLULAR)
            assertThat(call.startedAt).isEqualTo(Moment(STARTED))
            assertThat(call.isActive).isTrue()
            assertThat(state.pendingStops).containsExactly(SensitiveActionKind.CODE_MESSAGE, Moment(STARTED.plusSeconds(30)))
            assertThat(dao.journal().first().map { it.type }).containsExactly("STOP")
        } finally {
            database.close()
        }
    }

    /** A signal stamped on both clocks, after the update, against a call stamped before it on the civil clock alone. */
    @Test
    fun aCallInProgressDuringTheUpdateStillProtects() = runTest {
        writeVersion1()
        helper.runMigrationsAndValidate(NAME, 2, true).close()

        val database = Room.databaseBuilder(context, CallHaltDatabase::class.java, NAME).build()
        try {
            val code = SensitiveAction(
                Moment(STARTED.plusSeconds(120), Uptime(9, Duration.ofHours(3))),
                SensitiveActionKind.BANK_VALIDATION_REQUEST,
            )
            val decisions = RoomDetectorStore(database).apply { state ->
                DetectionEngine().process(state, code, ProtectionSettings())
            }

            assertThat(decisions.filterIsInstance<Decision.ShowStop>()).hasSize(1)
            // Written by version 2: the new screen keeps its uptime, the old call still has none.
            val stops = database.detectorDao().pendingStops().associateBy { it.trigger }
            assertThat(stops.getValue(SensitiveActionKind.BANK_VALIDATION_REQUEST.name).shownBoot).isEqualTo(9)
            assertThat(database.detectorDao().callSessions().single().startedBoot).isNull()
        } finally {
            database.close()
        }
    }

    /** A call in progress, a STOP unanswered, a journal line: what the S24 FE held when version 2 was installed. */
    private fun writeVersion1() {
        context.deleteDatabase(NAME)
        helper.createDatabase(NAME, 1).apply {
            execSQL(
                "INSERT INTO call_session (channel, direction, counterpart_kind, counterpart_number, " +
                    "counterpart_in_contacts, started_at, ended_at, long_call_warned) " +
                    "VALUES ('CELLULAR', 'INCOMING', 'UNREADABLE', NULL, 0, ${STARTED.toEpochMilli()}, NULL, 0)",
            )
            execSQL(
                "INSERT INTO pending_stop (`trigger`, shown_at) VALUES ('CODE_MESSAGE', ${STARTED.plusSeconds(30).toEpochMilli()})",
            )
            execSQL(
                "INSERT INTO journal_entry (at, type, `trigger`, warning, choice, source, counterpart_kind, " +
                    "counterpart_number, counterpart_in_contacts) " +
                    "VALUES (${STARTED.plusSeconds(30).toEpochMilli()}, 'STOP', 'CODE_MESSAGE', NULL, NULL, NULL, " +
                    "'UNREADABLE', NULL, NULL)",
            )
            close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
        val STARTED: Instant = Instant.parse("2026-11-04T09:00:00Z")
    }
}
