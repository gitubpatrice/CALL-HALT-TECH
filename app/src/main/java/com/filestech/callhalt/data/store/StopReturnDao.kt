package com.filestech.callhalt.data.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Raw access to the STOP screens left that come back (FN-4). Only [RoomDetectorStore] writes, inside the transaction
 * that writes the rest of the detector's state.
 */
@Dao
interface StopReturnDao {

    @Query("SELECT * FROM stop_return")
    suspend fun stopReturns(): List<StopReturnEntity>

    @Query("DELETE FROM stop_return")
    suspend fun deleteStopReturns()

    @Insert
    suspend fun insertStopReturns(rows: List<StopReturnEntity>)
}
