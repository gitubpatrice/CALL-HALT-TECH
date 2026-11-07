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
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.InMemoryDetectorStore
import com.filestech.callhalt.platform.noGuardian
import com.filestech.callhalt.platform.noWakeUps
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.warning.WarningPoster
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

/**
 * What a notification is to the engine — and, through the REAL engine, what the listener relies on it for: a request
 * stops the user during a call only; a repeat is one STOP while that screen is unanswered, and a new one after.
 */
class NotificationSignalTest {

    private val store = InMemoryDetectorStore()
    private val shown = mutableListOf<Decision.ShowStop>()
    private val launcher = StopLauncher { stop ->
        shown += stop
        true // in front of the user
    }

    /** A notification is never a warning: a request outside a call is the user's own. */
    private val warnings = WarningPoster { error("no warning expected") }
    private val processSignal = ProcessSignalUseCase(
        DetectionEngine(),
        store,
        SettingsSource { ProtectionSettings() },
        RestartSource { null },
        LiveCallSource { null },
        AndroidDecisionExecutor(launcher, warnings, noGuardian, noWakeUps),
    )

    @ParameterizedTest
    @ValueSource(strings = [CREDIT_COOP, "fr.banquepopulaire.cyberplus", "com.example.other"])
    fun `a request on the Sécur'Pass channel asks to approve an operation, from whichever app`(packageName: String) {
        assertThat(notificationSignal(packageName, "securpass_id", sms = null, at(0)))
            .isEqualTo(SensitiveAction(at(0), SensitiveActionKind.BANK_VALIDATION_REQUEST, source = packageName))
    }

    /** A channel is an identifier in the app's code: matched exactly, never as words that look alike. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["miscellaneous", "", "SECURPASS_ID", "securpass", "securpass_id ", "securpass_id_2"])
    fun `any other channel is nothing to the engine`(channelId: String?) {
        assertThat(notificationSignal(CREDIT_COOP, channelId, sms = null, at(0))).isNull()
    }

    /** The texts measured on 2026-11-02 (emulators and S24 FE), digits of the measurement put back. */
    @Test
    fun `from the SMS app, a code is a code message, from the SMS app`() {
        val code = SmsMessage.Text("Votre code de validation est 482913. Ne le communiquez a personne.")
        assertThat(notificationSignal(SMS_APP, "incoming_messages", code, at(0)))
            .isEqualTo(SensitiveAction(at(0), SensitiveActionKind.CODE_MESSAGE, source = SMS_APP))
    }

    /** Coherence audit of 2026-11-07, C1: a failure to read leaned on the silent side. */
    @Test
    fun `an SMS notification the app cannot read counts as masked, so as a code, never as none`() {
        var logged: Exception? = null
        val read = latestSmsOrMasked({ error("a maker's own extras") }) { logged = it }

        assertThat(logged).isNotNull()
        assertThat(read?.message).isEqualTo(SmsMessage.Masked)
        assertThat(read?.sentAtMillis).isEqualTo(0L)
        assertThat(notificationSignal(SMS_APP, "incoming_messages", read?.message, at(0))?.kind)
            .isEqualTo(SensitiveActionKind.CODE_MESSAGE)
    }

    @Test
    fun `a notification read without failure is kept as read`() {
        val read = latestSmsOrMasked({ null }) { error("no failure expected") }

        assertThat(read).isNull()
    }

    /** Android hides a code it recognised (English, measured): its placeholder has no digit, the masking is the sign. */
    @Test
    fun `a message Android masked is a code message`() {
        assertThat(notificationSignal(SMS_APP, "incoming_messages", SmsMessage.Masked, at(0))?.kind)
            .isEqualTo(SensitiveActionKind.CODE_MESSAGE)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Rendez-vous demain a 14 h au cabinet.",
            "Crédit Coopératif : Virements - Ajout d'un bénéficiaire le 02/11/2026 sur internet. " +
                "Si vous n'avez pas initié cette opération, contactez votre agence.",
        ],
    )
    fun `any other message is nothing to the engine`(text: String) {
        assertThat(notificationSignal(SMS_APP, "incoming_messages", SmsMessage.Text(text), at(0))).isNull()
    }

    @Test
    fun `during a call, a code by SMS shows a STOP`() = runTest {
        startCall()
        processSignal(checkNotNull(notificationSignal(SMS_APP, "msg", SmsMessage.Text("Code de confirmation : 271846"), at(20))))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.CODE_MESSAGE)
    }

    /** The system delivers one notification twice, 0.3 s apart (measured on Android 16 and 17). */
    @Test
    fun `during a call, a request shows a STOP, and a repeat while it is unanswered shows no second one`() = runTest {
        startCall()
        post(at(30))
        post(at(31))
        assertThat(shown.map { it.trigger }).containsExactly(SensitiveActionKind.BANK_VALIDATION_REQUEST)
        assertThat(store.journal).containsExactly(
            JournalEntry.Stop(at(30).wall, SensitiveActionKind.BANK_VALIDATION_REQUEST, CREDIT_COOP, Counterpart.Unreadable),
        )
    }

    /** The protective side, chosen: the listener cannot tell a repeat from a new request posted in its place. */
    @Test
    fun `once the STOP is answered, a repeat shows a new one`() = runTest {
        startCall()
        post(at(30))
        processSignal(StopAnswered(at(40), SensitiveActionKind.BANK_VALIDATION_REQUEST, StopChoice.CONTINUE))
        post(at(41))
        assertThat(shown).hasSize(2)
    }

    @Test
    fun `an unanswered STOP absorbs repeats for a minute, not longer`() = runTest {
        startCall()
        post(at(30))
        post(at(30 + DetectionEngine.UNANSWERED_STOP_HOLD.seconds))
        assertThat(shown).hasSize(2)
    }

    @Test
    fun `outside a call, a request is the user's own, and nothing happens`() = runTest {
        startCall()
        processSignal(CallEnded(at(60), CallChannel.CELLULAR))
        post(at(60 + AFTER_CALL_WINDOW_SECONDS + 1))
        assertThat(shown).isEmpty()
        assertThat(store.journal).isEmpty()
    }

    private suspend fun startCall() {
        processSignal(CallStarted(at(0), CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable))
    }

    private suspend fun post(at: Moment) {
        processSignal(checkNotNull(notificationSignal(CREDIT_COOP, "securpass_id", sms = null, at)))
    }

    private fun at(seconds: Long) = Moment(T0.plusSeconds(seconds))

    private companion object {
        const val CREDIT_COOP = "com.credit_coop.android.mobilebanking"
        const val SMS_APP = "com.filestech.sms"
        val AFTER_CALL_WINDOW_SECONDS = ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.seconds
        val T0: Instant = Instant.parse("2026-11-02T09:00:00Z")
    }
}
