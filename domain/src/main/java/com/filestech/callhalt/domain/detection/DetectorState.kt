package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * Everything the engine remembers between two signals. It must survive the process: Android kills the
 * app between the call starting and the SMS arriving minutes later, so the store persists it
 * (docs/ARCHITECTURE.md, D-004).
 */
data class DetectorState(
    /** The latest call per channel, in progress or ended. */
    val calls: Map<CallChannel, CallSession> = emptyMap(),
    /**
     * STOP screens shown and not answered yet, by trigger, with the time they were shown. A burst of
     * signals shows one screen, not five; an ANSWER re-arms the trigger at once, so that "it did not
     * work, validate again" is stopped a second time.
     */
    val pendingStops: Map<SensitiveActionKind, Moment> = emptyMap(),
    /** Channels whose current call already had its long-call reminder: one reminder per call. */
    val longCallWarned: Set<CallChannel> = emptySet(),
) {
    init {
        require(calls.all { (channel, call) -> call.channel == channel }) { "A call is filed under its own channel." }
        // The store keeps this flag on the call's own row: a reminder without its call could not be stored.
        require(longCallWarned.all { it in calls }) { "A long-call reminder belongs to a tracked call." }
    }
}
