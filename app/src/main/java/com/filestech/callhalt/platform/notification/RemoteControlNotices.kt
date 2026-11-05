package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.platform.install.REMOTE_CONTROL_APPS
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A remote-control app at work, seen in its notifications (docs/ARCHITECTURE.md, D-032): each NEW notification of an app
 * of [REMOTE_CONTROL_APPS] is one — its service started (sharing the screen needs a foreground service, and so a
 * notification, from Android 10), a connection asked for, a session established (measured with RustDesk, Android 16).
 * The engine stops the user during a call or just after it, and does nothing outside.
 *
 * New, not updated: the app updates its notifications all along (RustDesk re-posts its service's at each connection),
 * and the system may deliver one twice. A notification is new when its key is not among the ones still shown. Those
 * shown when the listener connects are not new — a service that runs all day (TeamViewer Host, AirDroid) is not one that
 * just started whenever the listener is bound again — unless posted in the last [RECONNECTED_WITHIN]: the listener was
 * away (its process killed, bound again a second later, measured), and what came meanwhile came during the call perhaps
 * (review of 2026-11-05, 1). Android's own group summary, posted under the app's name, is no event of the app. Nothing
 * of the notification is read but its app, its key, when it was posted and whether it is a summary.
 *
 * One per process, not per listener: Android destroys the listener when it unbinds it, and binds a new one (measured,
 * 2026-11-05: a second STOP for a notification seen unchanged, until this). Touched from the listener's callbacks only,
 * on the main thread.
 */
@Singleton
class RemoteControlNotices @Inject constructor() {

    /** Each key still shown, with when it was last posted. */
    private val shown = mutableMapOf<String, Long>()

    /**
     * The listener connected, at [at], and [notices] are shown: none of them is new, but those posted in the last
     * [RECONNECTED_WITHIN] that this process did not see as they are now, which are returned as the app at work. Seen
     * unchanged, it was told already (GPT review of 2026-11-05, 1); posted again meanwhile — RustDesk re-posts under the
     * same key at each connection — it is news.
     */
    fun alreadyShown(notices: List<Notice>, at: Moment): List<SensitiveAction> {
        val seen = shown.toMap()
        shown.clear()
        val listed = notices.filter { watches(it) && !it.groupSummary }
        listed.forEach { shown[it.key] = it.postedAtMillis }
        val since = at.wall.minus(RECONNECTED_WITHIN).toEpochMilli()
        val meanwhile = listed.filter { it.postedAtMillis >= since && seen[it.key] != it.postedAtMillis }
        return meanwhile.map { atWork(it, at) }.distinctBy { it.source }
    }

    /**
     * The shown notifications could not be read when the listener connected: everything is forgotten. A key kept could
     * be one removed meanwhile, posted again: a start missed. Forgotten, the next update of a service running all day may
     * read as new — a STOP too many, the protective side (GPT review of 2026-11-05, 2).
     */
    fun forget() {
        shown.clear()
    }

    /** An app of the list: its notifications are this, and nothing else (no bank request, no code). */
    fun watches(notice: Notice): Boolean = notice.packageName in REMOTE_CONTROL_APPS

    /** What a posted notification of an app of the list is to the engine: the app at work, when it is a new one. */
    fun posted(notice: Notice, at: Moment): SensitiveAction? {
        if (!watches(notice) || notice.groupSummary) return null
        val new = notice.key !in shown
        shown[notice.key] = notice.postedAtMillis
        return if (new) atWork(notice, at) else null
    }

    fun removed(key: String) {
        shown -= key
    }

    private fun atWork(notice: Notice, at: Moment) =
        SensitiveAction(at, SensitiveActionKind.REMOTE_CONTROL_OPENED, source = notice.packageName)

    /**
     * Of a notification, what this reads: its app, its key (unique among those shown), when it was posted (the phone's
     * civil clock, as Android stamps it), whether it is a group summary.
     */
    data class Notice(
        val packageName: String,
        val key: String,
        val postedAtMillis: Long = 0,
        val groupSummary: Boolean = false,
    )

    companion object {
        /** The listener bound again a second after its process was killed (measured): this covers it, and more. */
        val RECONNECTED_WITHIN: Duration = Duration.ofMinutes(2)
    }
}
