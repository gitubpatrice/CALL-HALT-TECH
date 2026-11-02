package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.platform.stop.StopLauncher
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the engine's decisions out ([DecisionExecutor]'s contract), as far as this step of the prototype can.
 *
 * - **STOP**, first of all: the screen ([StopLauncher]). One that did not get through makes this executor
 *   throw — the only way the contract allows — and the engine journals it as NOT shown. The full-screen
 *   notification the contract names as a fallback is NOT built yet (docs/ARCHITECTURE.md, P-19).
 * - **Warning, text to the relative, wake-up**: not carried out yet, each with its own step (docs/ARCHITECTURE.md,
 *   § 6 and P-8). A wake-up is only asked for a long call with an unknown number, and the number is not read yet.
 */
@Singleton
class AndroidDecisionExecutor @Inject constructor(private val launcher: StopLauncher) : DecisionExecutor {

    override suspend fun execute(decisions: List<Decision>) {
        val notShown = decisions.filterIsInstance<Decision.ShowStop>().count { !launcher.show(it) }
        decisions.filterNot { it is Decision.ShowStop }.forEach { decision ->
            Timber.i("Not carried out at this step: %s", decision::class.simpleName)
        }
        check(notShown == 0) { "STOP screen not shown: $notShown" }
    }
}
