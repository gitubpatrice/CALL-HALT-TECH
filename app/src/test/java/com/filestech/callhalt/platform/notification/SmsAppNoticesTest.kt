package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.platform.notification.SmsAppNotices.Companion.FULL
import com.filestech.callhalt.platform.notification.SmsAppNotices.Companion.RECENT_MIN
import com.filestech.callhalt.platform.notification.SmsAppNotices.Companion.RECENT_MS
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The SMS app's notifications shown, as Android caps them (publication audit, N-3, D-040): a burst is the count going up
 * past 30, then 45, with enough of them new — never the same notification updated, nor a phone that keeps its unread
 * messages and gets one more (review of 2026-11-09: SMS Tech posts one notification per message).
 */
class SmsAppNoticesTest {

    private val notices = SmsAppNotices()
    private val now = 10 * RECENT_MS

    /** [count] new notifications of [app], one a second, from [first] on; the bursts they made. */
    private fun post(count: Int, first: Int = 0, app: String = SMS, at: Long = now): List<Int> =
        (first until first + count).filter { notices.posted(app, "key $it", at + it * 1_000L) }

    @Test
    fun `a flood is a burst when the count goes past 30, then past 45`() {
        assertThat(post(55)).containsExactly(29, 44).inOrder()
    }

    /** Google Messages updates a conversation's notification; a notification delivered twice is the same. */
    @Test
    fun `the same notification posted again is no more of them`() {
        repeat(60) { assertThat(notices.posted(SMS, "the bank", now + it)).isFalse() }
        assertThat(notices.shown.value).containsExactly(SMS, 1)
    }

    /** A phone that keeps its unread messages for days, one more during a call: not "many at once". */
    @Test
    fun `past 30 with few of them new is no burst`() {
        notices.connected(SMS, (0 until 28).associate { "old $it" to now - RECENT_MS - 1 })
        assertThat(post(RECENT_MIN - 1)).isEmpty()
        assertThat(notices.shown.value[SMS]).isEqualTo(28 + RECENT_MIN - 1)
    }

    @Test
    fun `past 30 with enough of them new is one`() {
        notices.connected(SMS, (0 until 30 - RECENT_MIN).associate { "old $it" to now - RECENT_MS - 1 })
        assertThat(post(RECENT_MIN)).containsExactly(RECENT_MIN - 1)
    }

    /** Already past 30 by a few new ones when the flood starts: the next mark, 45, catches it, before Android's cap. */
    @Test
    fun `a flood on a phone just under 30 is caught past 45`() {
        notices.connected(SMS, (0 until 28).associate { "old $it" to now - RECENT_MS - 1 })
        assertThat(post(22)).containsExactly(16)
    }

    /** Messages read take their notifications away: a new flood is a new burst. */
    @Test
    fun `read and flooded again, a burst again`() {
        post(30)
        (0 until 30).forEach { notices.removed("key $it") }
        assertThat(notices.shown.value[SMS] ?: 0).isEqualTo(0)
        assertThat(post(30, first = 100)).containsExactly(129)
    }

    /** At connection, what is shown replaces what was kept: removals missed while the listener was away. */
    @Test
    fun `the listener connecting counts again from what is shown, without a burst`() {
        post(20)
        notices.connected(SMS, (0 until 40).associate { "shown $it" to now })
        assertThat(notices.shown.value[SMS]).isEqualTo(40)
        notices.connected(null, emptyMap())
        assertThat(notices.shown.value).isEmpty()
    }

    @Test
    fun `each app is counted apart`() {
        assertThat(post(29, app = OTHER)).isEmpty()
        assertThat(post(29, first = 100)).isEmpty()
        assertThat(notices.shown.value).containsExactly(SMS, 29, OTHER, 29)
    }

    @Test
    fun `the home screen says the default SMS app is full from 45, and only for it`() {
        assertThat(SmsAppNotices.full(mapOf(SMS to FULL), SMS)).isTrue()
        assertThat(SmsAppNotices.full(mapOf(SMS to FULL - 1), SMS)).isFalse()
        assertThat(SmsAppNotices.full(mapOf(OTHER to FULL), SMS)).isFalse()
        assertThat(SmsAppNotices.full(mapOf(SMS to FULL), null)).isFalse()
    }

    private companion object {
        const val SMS = "com.google.android.apps.messaging"
        const val OTHER = "com.filestech.sms"
    }
}
