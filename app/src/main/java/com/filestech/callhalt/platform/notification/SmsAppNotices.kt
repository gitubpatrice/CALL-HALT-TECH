package com.filestech.callhalt.platform.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The default SMS app's notifications shown now (publication audit, N-3; docs/ARCHITECTURE.md, D-040) — what Android
 * caps: past 50 for one app, it shows its next ones no more, and a code SMS goes unseen (measured: 55 numbers, Google
 * Messages, the code during the call without a notification, no STOP). A flooding service fills that cap on purpose.
 *
 * A burst is the count going up past [STOP_AT] — 30, then 45, long before the cap — while at least [RECENT_MIN] of them
 * came in the last [RECENT_MS]: "many at once", not a phone that keeps its unread messages for days and gets one more.
 * Counted by notification, as Android counts them: one per conversation for Google Messages (measured), one per message
 * for SMS Tech (its sources) — a message read takes its own away. [shown] also tells the home screen when the app is
 * near its cap before any call ([FULL]): then nothing new shows, a code included, and nothing can see it.
 *
 * What it keeps, in memory only: the key of each notification of the SMS app still shown, and when it last came — never
 * what it says. One per process, not per listener, as [RemoteControlNotices]: Android destroys the listener when it
 * unbinds it. Changed from the listener's callbacks only, on the main thread; [shown] read anywhere.
 */
@Singleton
class SmsAppNotices @Inject constructor() {

    /** Each notification shown: its app, and when it was last posted (the monotonic clock, in ms). */
    private val notices = mutableMapOf<String, Notice>()

    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** How many notifications each SMS app shows now, by package. */
    val shown: StateFlow<Map<String, Int>> = counts.asStateFlow()

    /**
     * The listener connected, or the default SMS app changed, and [packageName] shows [shown] at [atMillis] — each key
     * with when it was posted (the monotonic clock): they replace what was kept, which the listener may have missed while
     * away. True when they make a burst against what was kept: a flood that came while the listener was away came during
     * the call perhaps (GPT review of 2026-11-09, 1). A time after [atMillis] (the clock changed) is taken as [atMillis].
     */
    fun connected(packageName: String?, shown: Map<String, Long>, atMillis: Long): Boolean {
        val before = packageName?.let(::countOf) ?: 0
        notices.clear()
        if (packageName != null) shown.forEach { (key, postedAt) -> notices[key] = Notice(packageName, minOf(postedAt, atMillis)) }
        publish()
        return packageName != null && burst(packageName, before, atMillis)
    }

    /**
     * [packageName] posted [key] at [atMillis]: true when that makes a burst. A notification updated keeps the time it
     * first came: a new message in a conversation already shown is not one more notification, nor a new one (GPT review
     * of 2026-11-09, 2).
     */
    fun posted(packageName: String, key: String, atMillis: Long): Boolean {
        val before = countOf(packageName)
        if (key !in notices) notices[key] = Notice(packageName, atMillis)
        publish()
        return burst(packageName, before, atMillis)
    }

    fun removed(key: String) {
        if (notices.remove(key) != null) publish()
    }

    private fun countOf(packageName: String) = notices.values.count { it.packageName == packageName }

    /** Gone past a mark, upward, from [before], with enough of them come in the last [RECENT_MS]. */
    private fun burst(packageName: String, before: Int, atMillis: Long): Boolean {
        val after = countOf(packageName)
        val recent = notices.values.count { it.packageName == packageName && atMillis - it.postedAt in 0..RECENT_MS }
        return STOP_AT.any { before < it && after >= it } && recent >= RECENT_MIN
    }

    private fun publish() {
        counts.value = notices.values.groupingBy { it.packageName }.eachCount()
    }

    private class Notice(val packageName: String, val postedAt: Long)

    internal companion object {
        /**
         * The default SMS app near Android's cap, by [shown]: what the home screen says (N-3, Patrice, 2026-11-09). Another
         * app's count does not matter: only the default one receives the SMS.
         */
        fun full(shown: Map<String, Int>, defaultSmsApp: String?): Boolean =
            defaultSmsApp != null && (shown[defaultSmsApp] ?: 0) >= FULL

        /** Gone past, upward: a burst. Far below Android's cap of 50; the second for a phone already over the first. */
        val STOP_AT = listOf(30, 45)

        /** Near the cap: said on the home screen before any call (Patrice, 2026-11-09). */
        const val FULL = 45

        /** "At once": that many of them in the last [RECENT_MS]. A flooding service sends dozens a minute. */
        const val RECENT_MIN = 5
        const val RECENT_MS = 600_000L
    }
}
