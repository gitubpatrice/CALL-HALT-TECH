package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.platform.guardian.GuardianNotifier
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the engine's decisions out ([DecisionExecutor]'s contract), as far as this step of the prototype can.
 *
 * - **STOP**, first of all: the screen, or the notification that opens it ([StopLauncher], docs/ARCHITECTURE.md,
 *   D-024). One that reached the user neither way makes this executor throw — the only way the contract allows —
 *   and the engine journals it as NOT shown.
 * - **Warning**: a notification ([WarningPoster], D-031). One that could not be posted throws nothing: the journal
 *   keeps the warning either way, and a throw would make a STOP shown with it read as not shown.
 * - **Text to the relative**: after the STOP, which comes first ([GuardianNotifier], D-034); one that fails throws
 *   nothing, for the same reason. Sent even when that STOP reached the user neither way (the phone blocked the screen,
 *   its notifications turned off): the user then saw nothing, and the relative is all the protection left — the code
 *   or the request did arrive during the call (review of 2026-11-07, 1: its text then says a little more than happened).
 * - **Wake-up**: in this process ([WakeUps], D-038): the return of a STOP left without an answer, and the wait for its
 *   answer (FN-4). Asked for a long call with an unknown number too, which the engine decides for no call today: the
 *   number is not read yet (docs/ARCHITECTURE.md, P-8).
 */
@Singleton
class AndroidDecisionExecutor @Inject constructor(
    private val launcher: StopLauncher,
    private val warnings: WarningPoster,
    private val guardian: GuardianNotifier,
    private val wakeUps: WakeUps,
) : DecisionExecutor {

    override suspend fun execute(decisions: List<Decision>) {
        val notShown = decisions.filterIsInstance<Decision.ShowStop>().count { !launcher.show(it) }
        decisions.filterNot { it is Decision.ShowStop }.forEach { decision ->
            when (decision) {
                is Decision.ShowWarning -> if (!posted(decision)) Timber.w("Warning not posted: %s", decision.reason)
                is Decision.NotifyGuardian -> told(decision)
                is Decision.ScheduleTick -> wakeUps.at(decision.at)
                // Taken out above: every STOP first.
                is Decision.ShowStop -> Unit
            }
        }
        check(notShown == 0) { "STOP screen not shown: $notShown" }
    }

    /** Whatever the phone throws while texting: thrown out of here, it would journal a STOP shown as NOT shown. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun told(decision: Decision.NotifyGuardian) {
        try {
            guardian.tell(decision.event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            Timber.w(failure, "Text to the relative failed")
        }
    }

    /**
     * Whatever the phone throws while posting (a notification service gone, a settings page it cannot resolve): thrown
     * out of here, it would journal as NOT shown a STOP decided with the warning (review of 2026-11-05, 2).
     */
    @Suppress("TooGenericExceptionCaught")
    private fun posted(warning: Decision.ShowWarning): Boolean = try {
        warnings.show(warning)
    } catch (failure: RuntimeException) {
        Timber.w(failure, "Warning failed")
        false
    }
}
