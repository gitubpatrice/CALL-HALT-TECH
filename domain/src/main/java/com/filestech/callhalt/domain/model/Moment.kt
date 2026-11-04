package com.filestech.callhalt.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Which boot of the phone, and how long after it — a clock no one can set (docs/ARCHITECTURE.md, P-13). [boot] is the
 * count Android keeps; [sinceBoot] runs through deep sleep, from the phone's own start: a restart of Android alone
 * raises the count without setting it back to zero. It can be negative: a moment carried over from an earlier boot at
 * the restart ([com.filestech.callhalt.domain.model.PhoneRestarted]) may fall before this boot's uptime began.
 */
data class Uptime(val boot: Int, val sinceBoot: Duration)

/**
 * When something happened, on the phone's two clocks (docs/ARCHITECTURE.md, P-13).
 *
 * [wall] is the civil time, the journal's: the user can set it back or forward, the network corrects it. [uptime],
 * when the phone gives it, cannot be set. Within one boot, moments are ordered and measured on it: a clock set back
 * during a call no longer makes the code that follows look older than the call. Two boots are ordered by their count,
 * every moment of a boot coming after every moment of the boots before it. Only when [uptime] is missing on either
 * side — a phone that does not count its boots, a row written before it was kept — does [wall] decide, as it did
 * before. The order is then the civil clock's, not consistent with [equals] when the two clocks disagree.
 */
data class Moment(val wall: Instant, val uptime: Uptime? = null) : Comparable<Moment> {

    override fun compareTo(other: Moment): Int {
        val mine = uptime
        val theirs = other.uptime
        return when {
            mine == null || theirs == null -> wall.compareTo(other.wall)
            mine.boot != theirs.boot -> mine.boot.compareTo(theirs.boot)
            else -> mine.sinceBoot.compareTo(theirs.sinceBoot)
        }
    }

    fun isBefore(other: Moment): Boolean = this < other

    fun isAfter(other: Moment): Boolean = this > other

    /** The same moment, [duration] later on both clocks. */
    operator fun plus(duration: Duration): Moment =
        Moment(wall.plus(duration), uptime?.let { it.copy(sinceBoot = it.sinceBoot.plus(duration)) })

    operator fun minus(duration: Duration): Moment = plus(duration.negated())

    /** Whether this moment belongs to a boot before [other]'s, by their count; on the civil clock when either does not say. */
    fun isOfEarlierBootThan(other: Moment): Boolean {
        val mine = uptime
        val theirs = other.uptime
        return if (mine != null && theirs != null) mine.boot < theirs.boot else isBefore(other)
    }

    /**
     * The time from this moment to [later] (negative if it is earlier): on the uptime within one boot; otherwise on the
     * civil clock, the only one two boots share — and which may have been set meanwhile.
     */
    fun until(later: Moment): Duration {
        val mine = uptime
        val theirs = later.uptime
        return if (mine != null && theirs != null && mine.boot == theirs.boot) {
            theirs.sinceBoot.minus(mine.sinceBoot)
        } else {
            Duration.between(wall, later.wall)
        }
    }
}
