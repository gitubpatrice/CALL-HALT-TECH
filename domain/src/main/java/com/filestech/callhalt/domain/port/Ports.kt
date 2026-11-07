package com.filestech.callhalt.domain.port

import com.filestech.callhalt.domain.detection.DetectorState
import com.filestech.callhalt.domain.detection.Evaluation
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.Signal

/**
 * The settings as the engine needs them, read fresh for each signal. An implementation that cannot read
 * them (I/O error, corrupted value) returns the protective defaults of [ProtectionSettings]: it never
 * throws, and never answers "protection off" by accident.
 */
fun interface SettingsSource {
    suspend fun current(): ProtectionSettings
}

/**
 * The phone's restarts, as far as the engine has not been told of them — asked before EVERY signal.
 *
 * A restart reported is the CURRENT boot: it ends only the calls of earlier boots, which cannot be in progress any
 * more, and reporting it twice changes nothing. So in doubt (a boot never seen, a memory failing) it is reported,
 * and it stops being reported only once the engine has committed it ([Restart.record]): staying silent instead would
 * leave the call it cut "in progress", and absorb the next real call into it (review of 2026-11-02). An
 * implementation that cannot count the phone's boots answers null. Nothing here throws.
 */
fun interface RestartSource {
    /** The current boot, unless it was already recorded. */
    suspend fun unreported(): Restart?
}

/**
 * The phone's call state read right now, as the call signal its broadcast would have carried — asked before every
 * sensitive action, so that what the broadcasts never told is told then (P-7). A call answered before the first unlock
 * after a start: the app cannot run then, and Android does not send the broadcast again once it can (FN-1, measured).
 * An end the app never heard: a call left "in progress" would make every action for hours look like one during a call.
 *
 * A call going on is a [com.filestech.callhalt.domain.model.CallStarted], none a
 * [com.filestech.callhalt.domain.model.CallEnded], both stamped [at]; the engine absorbs the one it already knows (a
 * start during the call, an end with no call in progress). Null when the phone only rings — nobody has answered yet —
 * or cannot be asked. Nothing here throws.
 */
fun interface LiveCallSource {
    suspend fun stateAt(at: Moment): Signal?
}

/**
 * The phone restarted, by [at] at the latest (see [com.filestech.callhalt.domain.model.PhoneRestarted]); [record] once
 * the engine has committed it, so that it is not reported again.
 */
class Restart(val at: Moment, val record: suspend () -> Unit)

/**
 * Where the detector's memory lives. An implementation MUST:
 * - run `step` on the latest committed state and commit its state AND journal in ONE transaction,
 *   serialising concurrent callers — an SMS and a notification can arrive in the same second;
 * - never lose the decisions because storage failed: a STOP screen that does not show because the disk is
 *   full fails on the wrong side. On a storage error, it evaluates `step` against the last state it knows
 *   in memory and returns the decisions anyway;
 * - let a CancellationException through, never swallow it.
 */
interface DetectorStore {
    suspend fun apply(step: (DetectorState) -> Evaluation): List<Decision>
}

/**
 * Carries the decisions out — screens, notifications, texts, wake-ups — once the store has committed.
 *
 * It shows every STOP FIRST, falling back to a full-screen notification when the screen cannot start. It
 * throws ONLY when a STOP could not be shown at all: the use case reads an exception as "no STOP of this
 * batch reached the user" and tells the engine. Any other failure (a text to the relative, a wake-up) it
 * handles itself — thrown, it would make a STOP that was shown read as one that was not.
 */
fun interface DecisionExecutor {
    suspend fun execute(decisions: List<Decision>)
}
