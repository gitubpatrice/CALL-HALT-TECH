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
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.noGuardian
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
        LiveCallSource { null },
        AndroidDecisionExecutor(launcher, WarningPoster { error("no warning expected") }, noGuardian),
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

    /** Google's suite installed from Play on a Samsung, which does not preinstall it (M4): signed by Google, exempt. */
    @Test
    fun `a feature not preinstalled but signed by its publisher is the phone's own`() {
        val asked = mutableListOf<Pair<String, String>>()
        val signedByPlay = { pkg: String, certificate: ByteArray ->
            asked += pkg to certificate.toHexString()
            certificate.toHexString() == TALKBACK_PLAY
        }
        assertThat(isPhoneFeature(TALKBACK_PACKAGE, preinstalled = { false }, signedByPlay)).isTrue()
        assertThat(asked).contains(TALKBACK_PACKAGE to TALKBACK_PLAY)
    }

    /** Google's other key, the one of the copies a phone preinstalls (and of Android TV's): exempt as well. */
    @Test
    fun `a feature signed with its publisher's other key is the phone's own`() {
        val signedAsPreinstalled = { _: String, certificate: ByteArray -> certificate.toHexString() == TALKBACK_PREINSTALLED }
        assertThat(isPhoneFeature(TALKBACK_PACKAGE, preinstalled = { false }, signedAsPreinstalled)).isTrue()
    }

    /** An app that took TalkBack's id, signed with its own key: not exempt. */
    @Test
    fun `a feature's id signed by another key is an app`() {
        assertThat(isPhoneFeature(TALKBACK_PACKAGE, preinstalled = { false }, signedWith = { _, _ -> false })).isFalse()
    }

    /** A TalkBack a phone maker or a ROM without Google preinstalls, signed with its own key: preinstalled, so exempt. */
    @Test
    fun `a feature preinstalled but signed by another key is the phone's own`() {
        assertThat(isPhoneFeature(TALKBACK_PACKAGE, preinstalled = { true }, signedWith = { _, _ -> false })).isTrue()
    }

    /** Samsung's features have no certificate here: preinstalled on every Samsung, they are exempt only so. */
    @Test
    fun `a feature without a known certificate is exempt only preinstalled`() {
        val signed = { _: String, _: ByteArray -> true }
        assertThat(isPhoneFeature("com.samsung.accessibility", preinstalled = { false }, signed)).isFalse()
        assertThat(isPhoneFeature("com.samsung.accessibility", preinstalled = { true }, signed)).isTrue()
    }

    /** Preinstalled or signed, an id off the list is never a feature: RustDesk preinstalled by a seller is still RustDesk. */
    @Test
    fun `an app off the list is never the phone's own`() {
        assertThat(isPhoneFeature("com.carriez.flutter_hbb", preinstalled = { true }, signedWith = { _, _ -> true })).isFalse()
    }

    /** Every certificate is of a listed feature, and a SHA-256: 32 bytes. */
    @Test
    fun `the certificates are of listed features and are SHA-256 digests`() {
        assertThat(SYSTEM_ACCESSIBILITY_SUITES).containsAtLeastElementsIn(SUITE_CERTIFICATES.keys)
        SUITE_CERTIFICATES.values.flatten().forEach { assertThat(it.hexToByteArray()).hasLength(SHA_256_BYTES) }
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
        const val TALKBACK_PACKAGE = "com.google.android.marvin.talkback"
        const val TALKBACK_PLAY = "7b5ad55180a48a1f30f35377c0f9e5f911bf942fb4cf83eba255a0ebf580beef"
        const val TALKBACK_PREINSTALLED = "93c9fc6d5af13c4e802dc1caf68f2e00688475b0b28f29487852cc9d550b6f27"
        const val SHA_256_BYTES = 32
        val T0: Instant = Instant.parse("2026-11-05T20:00:00Z")
    }
}
