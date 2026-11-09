package com.filestech.callhalt.data.store

import com.filestech.callhalt.domain.model.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The journal, read for the history screen (PROJET.md, function 8; publication audit, #1; D-041): its lines in the order
 * they were written, the oldest first — what [com.filestech.callhalt.domain.history.alerts] expects —, updated as new
 * ones are. Read only: the detector alone writes it ([RoomDetectorStore]). A line an older or newer version wrote that
 * this one cannot read is left out.
 */
@Singleton
class RoomJournal @Inject constructor(private val database: CallHaltDatabase) {

    val entries: Flow<List<JournalEntry>> =
        database.detectorDao().journal().map { rows -> rows.asReversed().mapNotNull(DetectorMapping::journalEntry) }
}
