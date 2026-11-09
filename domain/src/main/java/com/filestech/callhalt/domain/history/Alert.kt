package com.filestech.callhalt.domain.history

import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.WarningReason
import java.time.Instant

/**
 * One alert of the history (PROJET.md, function 8; publication audit, #1; docs/ARCHITECTURE.md, D-041): what the journal
 * keeps, read back for the user — and for the bank or the police, to whom the history is given.
 */
sealed interface Alert {
    val at: Instant

    /**
     * A STOP screen: what it was about, the app it came from ([source], a package name, when known), and what was chosen,
     * in order — a screen left without an answer, back once (FN-4), then answered: two answers. [notShown]: the phone did
     * not let it show.
     */
    data class Stop(
        override val at: Instant,
        val trigger: SensitiveActionKind,
        val source: String?,
        val answers: List<StopChoice> = emptyList(),
        val notShown: Boolean = false,
    ) : Alert

    /** A warning, a notification: today a remote-control app installed ([source], its package name). */
    data class Warning(override val at: Instant, val reason: WarningReason, val source: String?) : Alert
}

/**
 * The journal's lines as alerts, the newest first (Patrice, 2026-11-09: each STOP with its answer, on one line).
 * [entries] in the order they were written — never sorted by their times: a STOP and the "not shown" of the same decision
 * carry the same one, and a civil clock set back between a STOP and its answer would put the answer first (review of
 * 2026-11-09, 1). Each answer, and each "not shown", goes to the latest STOP of its trigger; one with no STOP before it
 * (written before the oldest line kept) is left out.
 */
fun alerts(entries: List<JournalEntry>): List<Alert> {
    val alerts = mutableListOf<Alert>()
    val latestStop = mutableMapOf<SensitiveActionKind, Int>()
    fun update(trigger: SensitiveActionKind, change: (Alert.Stop) -> Alert.Stop) {
        latestStop[trigger]?.let { index -> alerts[index] = change(alerts[index] as Alert.Stop) }
    }
    entries.forEach { entry ->
        when (entry) {
            is JournalEntry.Stop -> {
                latestStop[entry.trigger] = alerts.size
                alerts += Alert.Stop(entry.at, entry.trigger, entry.source)
            }
            is JournalEntry.Answer -> update(entry.trigger) { it.copy(answers = it.answers + entry.choice) }
            // Not shown after an answer: the screen it came back as (FN-4), not this one, which was seen and left.
            is JournalEntry.NotShown -> update(entry.trigger) { if (it.answers.isEmpty()) it.copy(notShown = true) else it }
            is JournalEntry.Warning -> alerts += Alert.Warning(entry.at, entry.reason, entry.source)
        }
    }
    return alerts.asReversed()
}
