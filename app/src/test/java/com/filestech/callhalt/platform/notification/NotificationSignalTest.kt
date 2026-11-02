package com.filestech.callhalt.platform.notification

import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Decision
import com.filestech.callhalt.domain.model.JournalEntry
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.stop.StopLauncher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

/**
 * What a notification is to the engine — and, through the REAL engine, what the listener relies on it for: a request
 * stops the user during a call only, and the same request posted again is one STOP, not two.
 */
class NotificationSignalTest {

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
        AndroidDecisionExecutor(launcher),
    )

    @ParameterizedTest
    @ValueSource(strings = [CREDIT_COOP, "fr.banquepopulaire.cyberplus", "com.example.other"])
    fun `a request on the Sécur'Pass channel asks to approve an operation, from whichever app`(packageName: String) {
        assertThat(notificationSignal(packageName, "securpass_id", at(0)))
            .isEqualTo(SensitiveAction(at(0), SensitiveActionKind.BANK_VALIDATION_REQUEST, source = packageName))
    }

    /** A channel is an identifier in the app's code: matched exactly, never as words that look alike. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["miscellaneous", "", "SECURPASS_ID", "securpass", "securpass_id ", "securpass_id_2"])
    fun `any other channel is nothing to the engine`(channelId: String?) {
        assertThat(notificationSignal(CREDIT_COOP, channelId, at(0))).isNull()
    }

    @Test
    fun `during a call, a request shows a STOP, and the same request posted again shows no second one`() = runTest {
        processSignal(CallStarted(at(0), CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable))
        post(at(30))
        post(at(31)) // the bank app updated its notification: posted again, a second later
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.BANK_VALIDATION_REQUEST)
        assertThat(store.journal).containsExactly(
            JournalEntry.Stop(at(30), SensitiveActionKind.BANK_VALIDATION_REQUEST, CREDIT_COOP, Counterpart.Unreadable),
        )
    }

    @Test
    fun `outside a call, a request is the user's own, and nothing happens`() = runTest {
        processSignal(CallStarted(at(0), CallChannel.CELLULAR, CallDirection.OUTGOING, Counterpart.Unreadable))
        processSignal(CallEnded(at(60), CallChannel.CELLULAR))
        post(at(60 + AFTER_CALL_WINDOW_SECONDS + 1))
        assertThat(shown).isEmpty()
        assertThat(store.journal).isEmpty()
    }

    private suspend fun post(at: Instant) {
        processSignal(checkNotNull(notificationSignal(CREDIT_COOP, "securpass_id", at)))
    }

    private fun at(seconds: Long): Instant = T0.plusSeconds(seconds)

    private companion object {
        const val CREDIT_COOP = "com.credit_coop.android.mobilebanking"
        val AFTER_CALL_WINDOW_SECONDS = ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.seconds
        val T0: Instant = Instant.parse("2026-11-02T09:00:00Z")
    }
}
