package com.filestech.callhalt.data.store

import androidx.room.withTransaction
import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.Evaluation
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.port.DetectorStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * [DetectorStore] on Room: the state and the journal of one signal are committed in ONE transaction, and
 * signals are processed one at a time (an SMS and a notification can arrive in the same second).
 *
 * When storage fails, the decisions are still made and returned — a STOP screen that does not show because
 * the disk is full would fail on the wrong side; a journal line missing is the lesser harm:
 * - a failure AFTER the state was read keeps the decision computed from it (GPT review, 1);
 * - a failure BEFORE decides from the last state this process knows;
 * - until a write succeeds again, that state in memory — not the database, which lacks it — is the truth the
 *   next signal is decided from, and the one written back (GPT review, 2). The journal lines of the failed
 *   transactions are lost.
 *
 * While it lasts, the home screen says so ([failing], docs/ARCHITECTURE.md, P-10), and tries storage again ([check]).
 *
 * ⚠️ LIMIT (docs/ARCHITECTURE.md, P-7): a process started AFTER the failure has no such state. It decides
 * from an empty one, and a call recorded before the failure is not seen — the STOP is missed. What closes
 * it is a live probe of the call state, not a second copy of the state; the telephony sensor does not make
 * one yet.
 */
@Singleton
class RoomDetectorStore @Inject constructor(
    private val database: CallHaltDatabase,
) : DetectorStore {

    private val mutex = Mutex()

    /** The last state this process decided from; guarded by [mutex]. */
    private var lastKnown: DetectorState? = null

    /** True while [lastKnown] holds what storage failed to keep; guarded by [mutex]. */
    private var unsaved = false

    private val failures = MutableStateFlow(false)

    /**
     * True since the last write failed, until one succeeds: the protection still decides, but a process started now
     * would not know the call in progress. False until storage is first tried ([apply] or [check]); and false in a
     * process started after a failure that lost the call — the class's LIMIT, which no write shows.
     */
    val failing: StateFlow<Boolean> = failures.asStateFlow()

    @Suppress("TooGenericExceptionCaught") // Any storage failure, by design — see the class comment.
    override suspend fun apply(step: (DetectorState) -> Evaluation): List<Decision> = mutex.withLock {
        var decided: Evaluation? = null
        val evaluation = try {
            database.withTransaction {
                val dao = database.detectorDao()
                step(base(dao)).also { result ->
                    decided = result
                    write(dao, result)
                }
            }.also { saved() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.e(failure, "Detector store unavailable: deciding from the state in memory")
            unsaved = true
            failures.value = true
            decided ?: step(lastKnown ?: DetectorState())
        }
        lastKnown = evaluation.state
        evaluation.decisions
    }

    /**
     * Storage tried again, without waiting for a signal: the state [apply] would decide from is written back — the one it
     * failed to keep (the user may have freed some space), or else the one just read. A write, not a read alone: a
     * storage that reads but no longer writes (turned read-only) would pass a read (review of 2026-11-03). Opening the
     * database is part of it: a migration missing fails there. Without it, a failure would show only after the next
     * call, and a repaired one would go on showing until then. A phone full is not always seen: writing the same rows
     * back may need no new space.
     */
    @Suppress("TooGenericExceptionCaught") // Any storage failure, as in apply.
    suspend fun check() {
        mutex.withLock {
            try {
                database.withTransaction {
                    val dao = database.detectorDao()
                    writeState(dao, base(dao))
                }
                saved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "Detector store still unavailable")
                failures.value = true
            }
        }
    }

    /** What storage failed to keep is the truth until written back (the class comment); otherwise, what it holds. */
    private suspend fun base(dao: DetectorDao): DetectorState =
        lastKnown.takeIf { unsaved } ?: DetectorMapping.toState(dao.callSessions(), dao.pendingStops())

    private fun saved() {
        unsaved = false
        failures.value = false
    }

    private suspend fun write(dao: DetectorDao, evaluation: Evaluation) {
        writeState(dao, evaluation.state)
        dao.insertJournal(evaluation.journal.map(DetectorMapping::journalRow))
    }

    private suspend fun writeState(dao: DetectorDao, state: DetectorState) {
        dao.deleteCallSessions()
        dao.insertCallSessions(DetectorMapping.callRows(state))
        dao.deletePendingStops()
        dao.insertPendingStops(DetectorMapping.stopRows(state))
    }
}
