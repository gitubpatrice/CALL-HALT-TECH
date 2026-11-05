package com.filestech.callhalt.platform.accessibility

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
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * An app given the accessibility access (D-033): a service of another app that appears in Android's setting — and,
 * through the REAL engine, a STOP during a call or just after it, nothing outside. RustDesk's service as measured.
 */
class AccessibilityGrantsTest {

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
    fun `the setting is read as its services, none when empty or missing`() {
        assertThat(enabledServices("$RUSTDESK_INPUT:$TALKBACK")).containsExactly(RUSTDESK_INPUT, TALKBACK)
        assertThat(enabledServices("")).isEmpty()
        assertThat(enabledServices(null)).isEmpty()
        assertThat(enabledServices(":$RUSTDESK_INPUT:")).containsExactly(RUSTDESK_INPUT)
    }

    /** Android may rewrite an entry from one form to the other: the same service, never a new one. */
    @Test
    fun `a service written short or long is the same service`() {
        val short = "com.carriez.flutter_hbb/.InputService"
        assertThat(enabledServices(short)).containsExactly(RUSTDESK_INPUT)
        assertThat(accessibilityGranted(enabledServices(RUSTDESK_INPUT), enabledServices(short), OWN, at(0))).isNull()
        assertThat(accessibilityGranted(enabledServices(short), enabledServices("$RUSTDESK_INPUT:$TALKBACK"), OWN, at(0))?.source)
            .isEqualTo("com.google.android.marvin.talkback")
    }

    @Test
    fun `a service that appears is its app given the access`() {
        assertThat(accessibilityGranted(setOf(TALKBACK), setOf(TALKBACK, RUSTDESK_INPUT), OWN, at(0)))
            .isEqualTo(SensitiveAction(at(0), SensitiveActionKind.ACCESSIBILITY_ENABLED, source = "com.carriez.flutter_hbb"))
    }

    @Test
    fun `a service turned off, or the same list again, is nothing`() {
        assertThat(accessibilityGranted(setOf(TALKBACK, RUSTDESK_INPUT), setOf(TALKBACK), OWN, at(0))).isNull()
        assertThat(accessibilityGranted(setOf(TALKBACK), setOf(TALKBACK), OWN, at(0))).isNull()
    }

    /** A daughter guiding her mother to turn TalkBack on, or its volume-keys shortcut: the phone's own feature. */
    @Test
    fun `a feature of the phone, preinstalled, is nothing`() {
        val preinstalled = { pkg: String -> pkg in SYSTEM_ACCESSIBILITY_SUITES }
        assertThat(accessibilityGranted(emptySet(), setOf(TALKBACK), OWN, at(0), preinstalled)).isNull()
    }

    /** An app that took TalkBack's id where the real one is not installed: not preinstalled, so not exempt. */
    @Test
    fun `an app under a feature's id, not preinstalled, is an app given the access`() {
        assertThat(accessibilityGranted(emptySet(), setOf(TALKBACK), OWN, at(0)) { false }?.source)
            .isEqualTo("com.google.android.marvin.talkback")
    }

    /** Two services in one change, a feature and RustDesk: RustDesk is the one. */
    @Test
    fun `a feature turned on with an app does not hide the app`() {
        val preinstalled = { pkg: String -> pkg in SYSTEM_ACCESSIBILITY_SUITES }
        assertThat(accessibilityGranted(emptySet(), setOf(TALKBACK, RUSTDESK_INPUT), OWN, at(0), preinstalled)?.source)
            .isEqualTo("com.carriez.flutter_hbb")
    }

    /** The debug build's measuring service (P-15) is this app's own. */
    @Test
    fun `a service of this app is nothing`() {
        assertThat(accessibilityGranted(emptySet(), setOf("$OWN/.platform.debug.MeasureTapService"), OWN, at(0))).isNull()
    }

    @Test
    fun `during a call, the access given shows a STOP`() = runTest {
        startCall()
        grant(at(20))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.ACCESSIBILITY_ENABLED)
        assertThat(store.journal).containsExactly(
            JournalEntry.Stop(at(20).wall, SensitiveActionKind.ACCESSIBILITY_ENABLED, "com.carriez.flutter_hbb", Counterpart.Unreadable),
        )
    }

    @Test
    fun `just after a call, the access given shows a STOP`() = runTest {
        startCall()
        processSignal(CallEnded(at(60), CallChannel.CELLULAR))
        grant(at(120))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.ACCESSIBILITY_ENABLED)
    }

    /** A screen reader turned on, an app the user trusts: his own choice outside a call, nothing kept. */
    @Test
    fun `outside a call, the access given is nothing`() = runTest {
        grant(at(0))
        assertThat(shown).isEmpty()
        assertThat(store.journal).isEmpty()
    }

    private suspend fun startCall() {
        processSignal(CallStarted(at(0), CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable))
    }

    private suspend fun grant(at: Moment) {
        processSignal(checkNotNull(accessibilityGranted(emptySet(), setOf(RUSTDESK_INPUT), OWN, at)))
    }

    private fun at(seconds: Long) = Moment(T0.plusSeconds(seconds))

    private companion object {
        const val OWN = "com.filestech.callhalt"
        const val RUSTDESK_INPUT = "com.carriez.flutter_hbb/com.carriez.flutter_hbb.InputService"
        const val TALKBACK = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        val T0: Instant = Instant.parse("2026-11-05T20:00:00Z")
    }
}
