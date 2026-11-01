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

    /** Newest first; the id breaks ties between entries written in the same millisecond. */
    @Query("SELECT * FROM journal_entry ORDER BY at DESC, id DESC")
    fun journal(): Flow<List<JournalEntryEntity>>
}
