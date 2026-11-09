package com.filestech.callhalt.platform.install

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
import com.filestech.callhalt.domain.model.WarningReason
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.accessibility.SYSTEM_ACCESSIBILITY_SUITES
import com.filestech.callhalt.platform.noGuardian
import com.filestech.callhalt.platform.noWakeUps
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What an installation is to the engine — and, through the REAL engine, what the sensor relies on it for: during a
 * call or just after it, a STOP; outside, a warning, journalled. And the list the code matches is the one the manifest
 * declares: an id missing from `<queries>` is an app Android never tells about.
 */
class RemoteControlAppsTest {

    private val store = InMemoryDetectorStore()
    private val shown = mutableListOf<Decision.ShowStop>()
    private val launcher = StopLauncher { stop ->
        shown += stop
        true // in front of the user
    }
    private val warned = mutableListOf<Decision.ShowWarning>()
    private val warnings = WarningPoster { warning ->
        warned += warning
        true // posted
    }
    private val processSignal = ProcessSignalUseCase(
        DetectionEngine(),
        store,
        SettingsSource { ProtectionSettings() },
        RestartSource { null },
        LiveCallSource { null },
        AndroidDecisionExecutor(launcher, warnings, noGuardian, noWakeUps),
    )

    /** The remote-control apps (D-031), and the phone's own accessibility features (D-033): no more, no less. */
    @Test
    fun `the manifest declares exactly the apps the code matches`() {
        val blocks = manifestPackageBlocks()
        assertThat(blocks).hasSize(2)
        assertThat(blocks).contains(REMOTE_CONTROL_APPS.keys.toList())
        assertThat(blocks).contains(SYSTEM_ACCESSIBILITY_SUITES.toList())
    }

    /** A preinstalled remote-control app (TeamViewer for Doro) is never exempt as a feature of the phone. */
    @Test
    fun `no remote-control app is taken for a feature of the phone`() {
        assertThat(REMOTE_CONTROL_APPS.keys.intersect(SYSTEM_ACCESSIBILITY_SUITES)).isEmpty()
    }

    /** The name the warning gives: ours, one per app, never empty. */
    @Test
    fun `every app of the list has a name`() {
        assertThat(REMOTE_CONTROL_APPS.values.filter { it.isBlank() }).isEmpty()
    }

    @Test
    fun `a remote-control app's first install is a remote-control install`() {
        REMOTE_CONTROL_APPS.keys.forEach { packageName ->
            assertThat(installSignal(packageName, replacing = false, at(0)))
                .isEqualTo(SensitiveAction(at(0), SensitiveActionKind.REMOTE_CONTROL_INSTALLED, source = packageName))
        }
    }

    /** The app was there before the update, and was warned about when it came. */
    @Test
    fun `an update is nothing to the engine`() {
        assertThat(installSignal(ANYDESK, replacing = true, at(0))).isNull()
    }

    /** Matched exactly: an id is the app's identifier, never words that look alike. */
    @Test
    fun `any other app is nothing to the engine`() {
        listOf("com.example.other", "com.anydesk", "com.anydesk.anydeskandroid.plugin", "COM.ANYDESK.ANYDESKANDROID")
            .forEach { packageName -> assertThat(installSignal(packageName, replacing = false, at(0))).isNull() }
    }

    /**
     * D-039: an app of the list uninstalled takes its warning away; updated (removed, then added back), it keeps it — the
     * app is still there; any other app's removal touches nothing (Claude review of 2026-11-09, 4).
     */
    @Test
    fun `only an app of the list uninstalled, not updated, takes its warning away`() {
        assertThat(withdrawsWarning(ANYDESK, replacing = false)).isTrue()
        assertThat(withdrawsWarning(ANYDESK, replacing = true)).isFalse()
        assertThat(withdrawsWarning("com.example.other", replacing = false)).isFalse()
    }

    @Test
    fun `during a call, an install shows a STOP that offers to uninstall it, and its warning silent for after the call`() =
        runTest {
            startCall()
            install(at(20))
            assertThat(shown.map { it.trigger to it.source })
                .containsExactly(SensitiveActionKind.REMOTE_CONTROL_INSTALLED to ANYDESK)
            assertThat(store.journal).containsExactly(
                JournalEntry.Stop(at(20).wall, SensitiveActionKind.REMOTE_CONTROL_INSTALLED, ANYDESK, Counterpart.Unreadable),
            )
            // FN-3 (D-039): the warning, which opens the app's page, stays once the call is over.
            assertThat(warned).containsExactly(
                Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK, withStop = true),
            )
        }

    /** The adviser has the victim hang up, then guides the install by text, or calls back. */
    @Test
    fun `just after a call, an install shows a STOP`() = runTest {
        startCall()
        processSignal(CallEnded(at(60), CallChannel.CELLULAR))
        install(at(60 + AFTER_CALL_WINDOW_SECONDS - 1))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.REMOTE_CONTROL_INSTALLED)
    }

    /** Warned about even outside a call (PROJET.md, § 4.4): no STOP; a warning that names the app, and a journal line. */
    @Test
    fun `outside a call, an install is a warning, not a STOP`() = runTest {
        install(at(0))
        assertThat(shown).isEmpty()
        assertThat(warned).containsExactly(Decision.ShowWarning(WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK))
        assertThat(store.journal)
            .containsExactly(JournalEntry.Warning(at(0).wall, WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK))
    }

    /** A warning the phone refused (notifications off) stays in the journal, and breaks nothing. */
    @Test
    fun `a warning that could not be posted is still journalled`() = runTest {
        val refused = ProcessSignalUseCase(
            DetectionEngine(),
            store,
            SettingsSource { ProtectionSettings() },
            RestartSource { null },
            LiveCallSource { null },
            AndroidDecisionExecutor(launcher, { false }, noGuardian, noWakeUps),
        )
        refused(checkNotNull(installSignal(ANYDESK, replacing = false, at(0))))
        assertThat(store.journal)
            .containsExactly(JournalEntry.Warning(at(0).wall, WarningReason.REMOTE_CONTROL_INSTALLED, ANYDESK))
    }

    private suspend fun startCall() {
        processSignal(CallStarted(at(0), CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable))
    }

    private suspend fun install(at: Moment) {
        processSignal(checkNotNull(installSignal(ANYDESK, replacing = false, at)))
    }

    private fun at(seconds: Long) = Moment(T0.plusSeconds(seconds))

    /** The `<queries>` blocks made of packages only: the other ones name intents (the settings pages, the SMS apps). */
    private fun manifestPackageBlocks(): List<List<String>> {
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val queries = manifest.getElementsByTagName("queries").elements()
        val packagesOnly = queries.filter { block -> block.childElements().all { it.tagName == "package" } }
        return packagesOnly.map { block -> block.childElements().map { it.getAttributeNS(ANDROID_NS, "name") } }
    }

    private fun NodeList.elements(): List<Element> = (0 until length).map(::item).filterIsInstance<Element>()

    private fun Element.childElements(): List<Element> = childNodes.elements()

    private companion object {
        const val ANYDESK = "com.anydesk.anydeskandroid"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        val AFTER_CALL_WINDOW_SECONDS = ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.seconds
        val T0: Instant = Instant.parse("2026-11-04T09:00:00Z")
    }
}
