package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
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
 * - **Text to the relative, wake-up**: not carried out yet, each with its own step (docs/ARCHITECTURE.md, § 6 and P-8).
 *   A wake-up is only asked for a long call with an unknown number, and the number is not read yet.
 */
@Singleton
class AndroidDecisionExecutor @Inject constructor(
    private val launcher: StopLauncher,
    private val warnings: WarningPoster,
) : DecisionExecutor {

    override suspend fun execute(decisions: List<Decision>) {
        val notShown = decisions.filterIsInstance<Decision.ShowStop>().count { !launcher.show(it) }
        decisions.filterNot { it is Decision.ShowStop }.forEach { decision ->
            when {
                decision !is Decision.ShowWarning -> Timber.i("Not carried out at this step: %s", decision::class.simpleName)
                !posted(decision) -> Timber.w("Warning not posted: %s", decision.reason)
            }
        }
        check(notShown == 0) { "STOP screen not shown: $notShown" }
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
