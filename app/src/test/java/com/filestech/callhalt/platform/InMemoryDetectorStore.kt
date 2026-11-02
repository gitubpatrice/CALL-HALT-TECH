package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.Evaluation
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.port.DetectorStore

/** The detector's memory and journal, in memory: what Room keeps on the phone (RoomDetectorStoreTest proves that). */
class InMemoryDetectorStore : DetectorStore {
    var state = DetectorState()
        private set
    val journal = mutableListOf<JournalEntry>()

    override suspend fun apply(step: (DetectorState) -> Evaluation): List<Decision> {
        val evaluation = step(state)
        state = evaluation.state
        journal += evaluation.journal
        return evaluation.decisions
    }
}
