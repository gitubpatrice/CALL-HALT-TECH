package com.filestech.callhalt.domain.detection

import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Moment
import java.time.Duration

/**
 * The one answer to "is the phone in a call, or was it a moment ago?" (PROJET.md, § 3). Every rule asks
 * it here: two rules computing it their own way would disagree at the edge of the window.
 */
object CallWindow {
    /**
     * A call tracked as in progress for longer than this is a call whose end was missed (process killed,
     * receiver disabled), not a call: it stops counting, rather than keep every later SMS "during a call"
     * forever. Scammers keep a victim on the line for hours, hence the margin.
     */
    val MAX_TRACKED_CALL: Duration = Duration.ofHours(MAX_TRACKED_CALL_HOURS)

    /**
     * True when [call] is still running at [at] — and has not outlived [MAX_TRACKED_CALL]. Never across a restart:
     * the moments of a later boot all come after the cap (see [Moment]).
     */
    fun inProgress(call: CallSession, at: Moment): Boolean =
        call.isActive && !at.isBefore(call.startedAt) && !at.isAfter(call.startedAt.plus(MAX_TRACKED_CALL))

    /**
     * The call [at] falls within — in progress, or ended at most [afterCall] earlier, bounds included —
     * or null. When several qualify, the earliest-started one: the call the scam began with.
     */
    fun callAt(state: DetectorState, at: Moment, afterCall: Duration): CallSession? =
        state.calls.values
            .filter { it.covers(at, afterCall) }
            .minByOrNull { it.startedAt }

    private fun CallSession.covers(at: Moment, afterCall: Duration): Boolean {
        val end = endedAt ?: return inProgress(this, at)
        return !at.isBefore(startedAt) && !at.isAfter(end.plus(afterCall))
    }
}

private const val MAX_TRACKED_CALL_HOURS = 8L
