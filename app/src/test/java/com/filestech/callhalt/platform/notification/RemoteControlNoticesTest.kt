package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopAnswered
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.notification.RemoteControlNotices.Notice
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A remote-control app at work, in its notifications (D-032): each new one of an app of the list, never an update — and,
 * through the REAL engine, a STOP during a call or just after it, nothing outside. The notifications are RustDesk's,
 * measured on Android 16 (2026-11-05): its service's (id 1), a connection asked (1965), Android's group summary (0).
 */
class RemoteControlNoticesTest {

    private val notices = RemoteControlNotices()
    private val store = InMemoryDetectorStore()
    private val shown = mutableListOf<Decision.ShowStop>()
    private val launcher = StopLauncher { stop ->
        shown += stop
        true // in front of the user
    }
    private val processSignal = ProcessSignalUseCase(
        DetectionEngine(),
        store,
        SettingsSource { ProtectionSettings() },
        RestartSource { null },
        AndroidDecisionExecutor(launcher, WarningPoster { error("no warning expected") }),
    )

    @Test
    fun `a new notification of a remote-control app is that app at work`() {
        assertThat(notices.posted(SERVICE, at(0)))
            .isEqualTo(SensitiveAction(at(0), SensitiveActionKind.REMOTE_CONTROL_OPENED, source = RUSTDESK))
    }

    /** RustDesk re-posts its service's at each connection; the system may deliver one twice. */
    @Test
    fun `an update of a notification still shown is nothing`() {
        notices.posted(SERVICE, at(0))
        assertThat(notices.posted(SERVICE, at(1))).isNull()
    }

    /** Measured: RustDesk removes "Do you accept?" and posts "Screen sharing established" under the same id. */
    @Test
    fun `once removed, the same notification posted again is new`() {
        notices.posted(REQUEST, at(0))
        notices.removed(REQUEST.key)
        assertThat(notices.posted(REQUEST, at(60))?.kind).isEqualTo(SensitiveActionKind.REMOTE_CONTROL_OPENED)
    }

    /** Each one new: a service started, then a connection asked. */
    @Test
    fun `two notifications of the app are two events`() {
        assertThat(notices.posted(SERVICE, at(0))).isNotNull()
        assertThat(notices.posted(REQUEST, at(30))).isNotNull()
    }

    /** A service that runs all day (TeamViewer Host, AirDroid) is not one that just started when the listener binds. */
    @Test
    fun `what is shown when the listener connects is not new`() {
        val longAgo = SERVICE.copy(postedAtMillis = at(0).wall.toEpochMilli())
        val signals = notices.alreadyShown(listOf(longAgo, Notice("com.example.other", "0|other|1|null|10001")), at(3600))
        assertThat(signals).isEmpty()
        assertThat(notices.posted(SERVICE, at(3601))).isNull()
        assertThat(notices.posted(REQUEST, at(3601))).isNotNull()
    }

    /** The listener's process killed during the call, bound again a second later: what came meanwhile is the app at work. */
    @Test
    fun `what was posted just before the listener connects is the app at work`() {
        val justBefore = SERVICE.copy(postedAtMillis = at(3590).wall.toEpochMilli())
        assertThat(notices.alreadyShown(listOf(justBefore), at(3600)))
            .containsExactly(SensitiveAction(at(3600), SensitiveActionKind.REMOTE_CONTROL_OPENED, source = RUSTDESK))
        // And it is known from then on: its next update is not new.
        assertThat(notices.posted(SERVICE, at(3601))).isNull()
    }

    @Test
    fun `just past the window, what is shown is not new`() {
        val window = RemoteControlNotices.RECONNECTED_WITHIN.seconds
        val past = SERVICE.copy(postedAtMillis = at(3600 - window - 1).wall.toEpochMilli())
        assertThat(notices.alreadyShown(listOf(past), at(3600))).isEmpty()
    }

    /** Seen and told before the listener went away, unchanged since: not a second STOP for the same start. */
    @Test
    fun `what was seen unchanged before the listener went away is not told again`() {
        val posted = SERVICE.copy(postedAtMillis = at(3590).wall.toEpochMilli())
        notices.posted(posted, at(3590))
        assertThat(notices.alreadyShown(listOf(posted), at(3600))).isEmpty()
    }

    /** Seen before, posted again meanwhile under the same key (RustDesk, at each connection): news. */
    @Test
    fun `what was posted again meanwhile under the same key is the app at work`() {
        notices.posted(SERVICE.copy(postedAtMillis = at(3500).wall.toEpochMilli()), at(3500))
        val again = SERVICE.copy(postedAtMillis = at(3595).wall.toEpochMilli())
        assertThat(notices.alreadyShown(listOf(again), at(3600))).hasSize(1)
    }

    /** Unreadable when the listener connects: forgotten, so a start under a known key is not missed. */
    @Test
    fun `once forgotten, a known key posted again is new`() {
        notices.posted(SERVICE, at(0))
        notices.forget()
        assertThat(notices.posted(SERVICE, at(10))).isNotNull()
    }

    /** Its notifications are this and nothing else: never read as a bank request or a code. */
    @Test
    fun `only the apps of the list are watched`() {
        assertThat(notices.watches(SERVICE)).isTrue()
        assertThat(notices.watches(Notice("com.credit_coop.android.mobilebanking", "0|cc|1|null|10300"))).isFalse()
    }

    /** One app, its service and a connection both posted meanwhile: one signal for the app. */
    @Test
    fun `two notifications of the app posted meanwhile are one signal`() {
        val recent = at(3595).wall.toEpochMilli()
        val signals = notices.alreadyShown(
            listOf(SERVICE.copy(postedAtMillis = recent), REQUEST.copy(postedAtMillis = recent)),
            at(3600),
        )
        assertThat(signals).hasSize(1)
    }

    /** Android's own summary, posted under the app's name. */
    @Test
    fun `a group summary is nothing`() {
        assertThat(notices.posted(Notice(RUSTDESK, "0|$RUSTDESK|0|g:Aggregate|10219", groupSummary = true), at(0))).isNull()
    }

    @Test
    fun `any other app's notification is nothing`() {
        assertThat(notices.posted(Notice("com.google.android.apps.messaging", "0|messaging|2|null|10100"), at(0))).isNull()
    }

    @Test
    fun `during a call, the app at work shows a STOP`() = runTest {
        startCall()
        post(SERVICE, at(20))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.REMOTE_CONTROL_OPENED)
        assertThat(store.journal).containsExactly(
            JournalEntry.Stop(at(20).wall, SensitiveActionKind.REMOTE_CONTROL_OPENED, RUSTDESK, Counterpart.Unreadable),
        )
    }

    /** The service started, then a connection a few seconds after: one screen while it is unanswered. */
    @Test
    fun `during a call, a burst of the app's notifications shows one STOP while it is unanswered`() = runTest {
        startCall()
        post(SERVICE, at(20))
        post(REQUEST, at(25))
        assertThat(shown).hasSize(1)
    }

    /** "Continue" chosen, then the session is established: a second chance. */
    @Test
    fun `once the STOP is answered, a new notification of the app shows a new one`() = runTest {
        startCall()
        post(SERVICE, at(20))
        processSignal(StopAnswered(at(30), SensitiveActionKind.REMOTE_CONTROL_OPENED, StopChoice.CONTINUE))
        post(REQUEST, at(40))
        assertThat(shown).hasSize(2)
    }

    @Test
    fun `just after a call, the app at work shows a STOP`() = runTest {
        startCall()
        processSignal(CallEnded(at(60), CallChannel.CELLULAR))
        post(SERVICE, at(120))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.REMOTE_CONTROL_OPENED)
    }

    /** Someone who uses it for himself (his own PC, his photos): nothing outside a call, nothing kept. */
    @Test
    fun `outside a call, the app at work is nothing`() = runTest {
        post(SERVICE, at(0))
        assertThat(shown).isEmpty()
        assertThat(store.journal).isEmpty()
    }

    private suspend fun startCall() {
        processSignal(CallStarted(at(0), CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable))
    }

    private suspend fun post(notice: Notice, at: Moment) {
        notices.posted(notice, at)?.let { processSignal(it) }
    }

    private fun at(seconds: Long) = Moment(T0.plusSeconds(seconds))

    private companion object {
        const val RUSTDESK = "com.carriez.flutter_hbb"
        val SERVICE = Notice(RUSTDESK, "0|$RUSTDESK|1|null|10219")
        val REQUEST = Notice(RUSTDESK, "0|$RUSTDESK|1965|null|10219")
        val T0: Instant = Instant.parse("2026-11-05T19:00:00Z")
    }
}
