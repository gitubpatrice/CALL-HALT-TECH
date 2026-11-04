package com.filestech.callhalt.data.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Raw access. Only [RoomDetectorStore] writes, inside its transaction; nothing else may. */
@Dao
interface DetectorDao {

    @Query("SELECT * FROM call_session")
    suspend fun callSessions(): List<CallSessionEntity>

    @Query("SELECT * FROM pending_stop")
    suspend fun pendingStops(): List<PendingStopEntity>

    @Query("DELETE FROM call_session")
    suspend fun deleteCallSessions()

    @Query("DELETE FROM pending_stop")
    suspend fun deletePendingStops()

    @Insert
    suspend fun insertCallSessions(rows: List<CallSessionEntity>)

    @Insert
    suspend fun insertPendingStops(rows: List<PendingStopEntity>)

    @Insert
    suspend fun insertJournal(rows: List<JournalEntryEntity>)

    /** The lines older than [before] (epoch milliseconds): past their time ([JournalRetention]). */
    @Query("DELETE FROM journal_entry WHERE at < :before")
    suspend fun deleteJournalBefore(before: Long)

    /**
     * All but the [keep] lines written last. The id is the order of writing (AUTOINCREMENT, never reused), whatever the
     * civil clock said; fewer lines than that, the subquery is NULL and nothing goes.
     */
    @Query("DELETE FROM journal_entry WHERE id <= (SELECT id FROM journal_entry ORDER BY id DESC LIMIT 1 OFFSET :keep)")
    suspend fun keepLastJournal(keep: Int)

    /** Newest first; the id breaks ties between entries written in the same millisecond. */
    @Query("SELECT * FROM journal_entry ORDER BY at DESC, id DESC")
    fun journal(): Flow<List<JournalEntryEntity>>
}
