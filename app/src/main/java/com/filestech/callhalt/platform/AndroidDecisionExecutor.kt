package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.port.DecisionExecutor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the engine's decisions out ([DecisionExecutor]'s contract), as far as this step of the prototype can.
 *
 * - **STOP**: no screen yet — it comes with step 2 (REPRISE.md). This executor says so the only way the contract
 *   allows: it throws, and the engine journals the STOP as not shown, never as shown. No sensor of a sensitive
 *   action exists at this step, so none is decided in practice.
 * - **Warning, text to the relative, wake-up**: not carried out yet, each with its own step (docs/ARCHITECTURE.md,
 *   § 6 and P-8). A wake-up is only asked for a long call with an unknown number, and the number is not read yet.
 */
@Singleton
class AndroidDecisionExecutor @Inject constructor() : DecisionExecutor {

    override suspend fun execute(decisions: List<Decision>) {
        decisions.filterNot { it is Decision.ShowStop }.forEach { decision ->
            Timber.i("Not carried out at this step: %s", decision::class.simpleName)
        }
        val stops = decisions.count { it is Decision.ShowStop }
        check(stops == 0) { "No STOP screen at this step: $stops not shown" }
    }
}
