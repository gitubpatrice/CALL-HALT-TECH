package com.filestech.callhalt.platform.notification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A flood of SMS from different numbers (publication audit, N-3; docs/ARCHITECTURE.md, D-040): the scammer has a service
 * send dozens of texts so that the code is buried among them — past 50 notifications, Android shows the SMS app's next
 * ones no more (measured: 55 numbers, Google Messages, the code SMS during the call without a notification, no STOP).
 * Counted in the default SMS app's notifications: [THRESHOLD] different conversations within [WINDOW_MS] are a burst,
 * long before that cap. A conversation is a notification's key, as the SMS app makes it (Google Messages: one per
 * conversation, measured) — the same number writing again updates its own, and a notification delivered twice is the
 * same — nothing of the message itself. Once told, the count starts over: another
 * [THRESHOLD] are another burst, which the engine absorbs while the STOP it caused shows.
 *
 * What it keeps, in memory only, for [WINDOW_MS] at most: those keys, and when each came. One per process, not per
 * listener, as [RemoteControlNotices]: Android destroys the listener when it unbinds it. Touched from the listener's
 * callbacks only, on the main thread.
 */
@Singleton
class SmsBurst @Inject constructor() {

    /** Each conversation of the window, with when its latest message came (the monotonic clock, in ms). */
    private val conversations = mutableMapOf<String, Long>()

    /** A message of [conversation] came at [atMillis] (the monotonic clock): true when it makes a burst. */
    fun received(conversation: String, atMillis: Long): Boolean {
        conversations.values.removeAll { came -> atMillis - came > WINDOW_MS || came > atMillis }
        conversations[conversation] = atMillis
        if (conversations.size < THRESHOLD) return false
        conversations.clear()
        return true
    }

    internal companion object {
        /**
         * Different conversations that make a burst: far below Android's cap of 50 notifications per app, far above what
         * a call and its quarter of an hour bring by chance (a reply or two, a delivery notice, an advertisement).
         */
        const val THRESHOLD = 10

        /** Within two minutes: a flooding service sends dozens a minute. */
        const val WINDOW_MS = 120_000L
    }
}
