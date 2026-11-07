package com.filestech.callhalt.platform.guardian

import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.Moment
import java.time.Duration

/**
 * How many texts the relative gets (docs/ARCHITECTURE.md, D-034). One per kind of STOP (Patrice, 2026-11-07: "un par
 * sorte d'alerte"): the same STOP again within [HOLD] sends none — a STOP for each of five codes would be five texts
 * saying one thing — while another kind sends one: a remote-control app after a code is worse news. **Each "Continue"
 * sends one** (Patrice, the same afternoon): each is a new act — the relative who called and was told "it is the bank,
 * all is well" must learn that the validations went on (review of 2026-11-07, faux positifs 3). And [MAX_PER_DAY] at
 * most in any [DAY], whatever happens: a sensor gone wrong must not send hundreds — some plans pay for each text, and
 * every phone abroad.
 *
 * In memory: a process that dies and restarts between two alerts of one kind sends the second one too, a text of more
 * rather than one of less. Measured on the phone's uptime within a boot ([Moment.until]): the civil clock set back does
 * not hold a text back.
 */
internal class GuardianLimiter {

    private val last = mutableMapOf<GuardianEvent, Moment>()
    private val sent = ArrayDeque<Moment>()

    /** Whether [event] may be texted at [at]. Nothing is counted until [record]: a text the phone refused holds nothing back. */
    @Synchronized
    fun allows(event: GuardianEvent, at: Moment): Boolean {
        sent.removeAll { !it.within(DAY, at) }
        val held = event is GuardianEvent.StopShown && last[kind(event)]?.within(HOLD, at) == true
        return sent.size < MAX_PER_DAY && !held
    }

    /** [event] was handed to the phone at [at]. */
    @Synchronized
    fun record(event: GuardianEvent, at: Moment) {
        last[kind(event)] = at
        sent.addLast(at)
    }

    /**
     * The text recorded for [event] at [at] did not go (no network, no SIM): it holds nothing back. Without this, the
     * same alert a minute later, the network back, would be held for a quarter of an hour (review of 2026-11-07, 2).
     */
    @Synchronized
    fun release(event: GuardianEvent, at: Moment) {
        val kind = kind(event)
        if (last[kind] == at) last.remove(kind)
        sent.remove(at)
    }

    /** Less than [span] before [at]; a moment "after" it — another boot, a civil clock set back — is not. */
    private fun Moment.within(span: Duration, at: Moment): Boolean {
        val elapsed = until(at)
        return !elapsed.isNegative && elapsed < span
    }

    /** The kind of an alert: during the call or after it, the same one. */
    private fun kind(event: GuardianEvent): GuardianEvent = when (event) {
        is GuardianEvent.StopShown -> event.copy(duringCall = false)
        is GuardianEvent.StopOverridden -> event.copy(duringCall = false)
        GuardianEvent.LongCallWithUnknown -> event
    }

    companion object {
        /** The window after a call's end, by default (ProtectionSettings): one scam, one text of each kind. */
        val HOLD: Duration = Duration.ofMinutes(15)
        val DAY: Duration = Duration.ofHours(24)
        const val MAX_PER_DAY = 20
    }
}
