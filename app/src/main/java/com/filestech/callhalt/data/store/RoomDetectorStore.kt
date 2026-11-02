package com.filestech.callhalt.data.store

import androidx.room.withTransaction
import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.Evaluation
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.port.DetectorStore
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

    @Suppress("TooGenericExceptionCaught") // Any storage failure, by design — see the class comment.
    override suspend fun apply(step: (DetectorState) -> Evaluation): List<Decision> = mutex.withLock {
        var decided: Evaluation? = null
        val evaluation = try {
            database.withTransaction {
                val dao = database.detectorDao()
                val base = lastKnown.takeIf { unsaved } ?: DetectorMapping.toState(dao.callSessions(), dao.pendingStops())
                step(base).also { result ->
                    decided = result
                    write(dao, result)
                }
            }.also { unsaved = false }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.e(failure, "Detector store unavailable: deciding from the state in memory")
            unsaved = true
            decided ?: step(lastKnown ?: DetectorState())
        }
        lastKnown = evaluation.state
        evaluation.decisions
    }

    private suspend fun write(dao: DetectorDao, evaluation: Evaluation) {
        dao.deleteCallSessions()
        dao.insertCallSessions(DetectorMapping.callRows(evaluation.state))
        dao.deletePendingStops()
        dao.insertPendingStops(DetectorMapping.stopRows(evaluation.state))
        dao.insertJournal(evaluation.journal.map(DetectorMapping::journalRow))
    }
}
