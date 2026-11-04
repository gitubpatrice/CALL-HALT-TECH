package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.StopAnswered
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Carries out the user's answer on the STOP screen and tells the engine — which journals it, texts the relative
 * when "Continue" was chosen, and frees the triggers (docs/ARCHITECTURE.md, § 3). The answer counts for EVERY
 * trigger the screen stood for ([StopSession.triggers]).
 */
@Singleton
class StopAnswers @Inject constructor(
    private val queue: SensorQueue,
    private val processSignal: Provider<ProcessSignalUseCase>,
    private val calls: CallControl,
    private val clock: PhoneClock,
) {
    /**
     * False when the choice needed the call ended and it could not be: nothing is recorded, and the screen stays —
     * the user, told the call is cut, would otherwise be left on the line with the scammer.
     */
    fun answer(session: StopSession, choice: StopChoice): Boolean {
        // The phone's clocks at the answer: the time every signal carries.
        val at = clock.now()
        // First of all, the call: every second on the line is the scammer's. "Call my bank" during a call is only
        // offered when the app can end it (StopText): the call ends BEFORE the dialler opens (D-011). The phone is
        // asked again at the press: with no call left, there is nothing to end.
        val endsTheCall = choice == StopChoice.HANG_UP || (choice == StopChoice.CALL_MY_BANK && session.current.onCall)
        if (endsTheCall && !calls.noCallGoingOn() && !calls.endCall()) {
            Timber.w("STOP answered %s, but the call was not ended: the screen stays", choice)
            return false
        }
        record(session, choice, at)
        return true
    }

    /** The screen was left without an answer: its triggers are freed, so that a repeat shows a new one. */
    fun left(session: StopSession) = record(session, StopChoice.LEFT, clock.now())

    private fun record(session: StopSession, choice: StopChoice, at: Moment) {
        session.triggers.forEach { trigger ->
            queue.submit("STOP answered: $choice") { processSignal.get()(StopAnswered(at, trigger, choice)) }
        }
    }
}
