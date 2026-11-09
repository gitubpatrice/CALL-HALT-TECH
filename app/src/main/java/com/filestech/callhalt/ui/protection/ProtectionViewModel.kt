package com.filestech.callhalt.ui.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.callhalt.data.settings.SettingsRepository
import com.filestech.callhalt.data.store.RoomDetectorStore
import com.filestech.callhalt.domain.model.ProtectionSettings
import com.filestech.callhalt.platform.access.Access
import com.filestech.callhalt.platform.access.AccessChecker
import com.filestech.callhalt.platform.access.PhoneSupport
import com.filestech.callhalt.platform.notification.ListenerConnection
import com.filestech.callhalt.platform.notification.SmsAppNotices
import com.filestech.callhalt.platform.notification.SmsPreview
import com.filestech.callhalt.platform.notification.previewHiddenNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** What the home and activation screens show (docs/ARCHITECTURE.md, D-022). */
data class ProtectionState(
    val granted: Set<Access>,
    /** The accesses the phone may refuse until the user allows the restricted settings ([AccessChecker.mayBeRestricted]). */
    val mayBeRestricted: Set<Access>,
    val afterCallMinutes: Long,
    /** [AccessChecker.support]: [PhoneSupport.NONE], nothing is asked; [PhoneSupport.ALERT_ONLY], the STOP by its notification. */
    val support: PhoneSupport = PhoneSupport.FULL,
    /** [RoomDetectorStore.failing]: the protection still decides, but forgets the call if the phone stops the app (P-10). */
    val storageFailing: Boolean = false,
    /** [AccessChecker.applicable]: the accesses this phone has, in the activation's order. */
    val applicable: List<Access> = Access.entries,
    /**
     * False once the notification access is granted but the system keeps its listener unbound, and asking it to bind
     * again did not help ([ListenerConnection], P-21, D-025): the protection is deaf, the access still granted.
     */
    val listening: Boolean = true,
    /** [SmsPreview]: the default SMS app hides its messages, and so every code (FN-2). Not the protection's state. */
    val smsPreviewHidden: Boolean = false,
    /**
     * [SmsAppNotices]: the default SMS app shows so many notifications that Android is about to show its next ones no
     * more, a code included (N-3, D-040). Not the protection's state.
     */
    val smsAppFull: Boolean = false,
) {
    /** In the order the activation asks for them. */
    val missing: List<Access> = applicable.filterNot { it in granted }

    /**
     * The first problem only, as the activation asks for one access at a time: the accesses before the storage. Where the
     * STOP comes by its notification alone ([PhoneSupport.ALERT_ONLY]), that notification is no fallback: without it, off.
     */
    val status: ProtectionStatus = when {
        support == PhoneSupport.NONE -> ProtectionStatus.UNSUPPORTED
        missing.any { it != Access.ALERTS || support == PhoneSupport.ALERT_ONLY } -> ProtectionStatus.OFF
        !listening -> ProtectionStatus.NOT_LISTENING
        Access.ALERTS in missing -> ProtectionStatus.NO_ALERT
        storageFailing -> ProtectionStatus.STORAGE_FAILING
        support == PhoneSupport.ALERT_ONLY -> ProtectionStatus.ALERT_ONLY
        else -> ProtectionStatus.ON
    }
}

/** What the home screen says of the protection. */
enum class ProtectionStatus {
    ON,

    /**
     * Weakened: only the STOP screen's fallback is missing (D-024). The screen still shows on most phones, so not "off"
     * (Patrice, 2026-11-03: "ok pour B"); but where the phone blocks it, the user would be told nothing.
     */
    NO_ALERT,

    /** Weakened: storage fails (P-10). */
    STORAGE_FAILING,

    /**
     * Weakened: every access granted, on a phone where no app may show in front of the others — the STOP comes by its
     * notification: on a phone asleep or locked, the screen it opens is there when the phone is woken; on a phone in use,
     * a touch away (D-030, measured; Patrice, 2026-11-04: "ok fais le").
     */
    ALERT_ONLY,

    /** Off in fact: every access granted, but the phone keeps the listener unbound (D-025). */
    NOT_LISTENING,
    OFF,
    UNSUPPORTED,
}

/**
 * The accesses are read again by [refresh], each time a screen comes back to the front: the user grants them in the
 * phone's settings, and can take them back there at any time. Storage is tried again at the same moment, and the
 * listener checked.
 */
@HiltViewModel
class ProtectionViewModel @Inject constructor(
    private val accesses: AccessChecker,
    private val store: RoomDetectorStore,
    private val listener: ListenerConnection,
    settings: SettingsRepository,
    private val smsPreview: SmsPreview,
    private val smsNotices: SmsAppNotices,
) : ViewModel() {

    private val read = MutableStateFlow(readAccesses())
    private val mayBeRestricted = Access.entries.filterTo(mutableSetOf(), accesses::mayBeRestricted)
    private val support = accesses.support()

    /** True once the listener was found unbound, asked to bind, and still unbound ([checkListener]). */
    private val unbound = MutableStateFlow(false)
    private var listenerCheck: Job? = null

    /** The default SMS app, read again with the accesses: the user may change it in the phone's settings. */
    private val defaultSms = MutableStateFlow(smsPreview.defaultSmsApp())

    val state: StateFlow<ProtectionState> =
        combine(read, settings.settings, store.failing, listener.connected, unbound) { now, current, failing, bound, gaveUp ->
            state(now, current.afterCallWindow.toMinutes(), failing, listening = bound || !gaveUp)
        }.combine(combine(smsPreview.hiddenIn, smsNotices.shown, defaultSms, ::smsApp)) { protection, sms ->
            sms(protection)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            smsApp(smsPreview.hiddenIn.value, smsNotices.shown.value, defaultSms.value)(
                state(read.value, ProtectionSettings.DEFAULT_AFTER_CALL_WINDOW.toMinutes(), store.failing.value, listening = true),
            ),
        )

    /** What the default SMS app hides, or can no longer show (FN-2, N-3): the home screen's own warnings. */
    private fun smsApp(hiddenIn: String?, shown: Map<String, Int>, defaultSmsApp: String?): (ProtectionState) -> ProtectionState {
        val hidden = previewHiddenNow(hiddenIn, defaultSmsApp)
        val full = SmsAppNotices.full(shown, defaultSmsApp)
        return { it.copy(smsPreviewHidden = hidden, smsAppFull = full) }
    }

    fun refresh() {
        read.value = readAccesses()
        defaultSms.value = smsPreview.defaultSmsApp()
        viewModelScope.launch { store.check() }
        checkListener()
    }

    /** The screen left the front: the listener is asked to bind only while the app is in front, where MIUI allows it. */
    fun leave() {
        listenerCheck?.cancel()
    }

    /**
     * Just after the app starts, the system may not have bound the listener yet: unbound after [LISTENER_GRACE], it is
     * asked to bind again, which works with the app in front (measured on the Redmi 9C); still unbound after as long, the
     * home screen says it. What was found last stays said until the listener is bound — not "on" again for the time of
     * a check (review of 2026-11-03, 2). Nothing is said while the access is not granted: the activation asks for it.
     */
    private fun checkListener() {
        listenerCheck?.cancel()
        if (Access.NOTIFICATIONS !in read.value.granted || listener.connected.value) {
            unbound.value = false
            return
        }
        listenerCheck = viewModelScope.launch {
            delay(LISTENER_GRACE)
            if (listener.connected.value) return@launch
            listener.rebind()
            delay(LISTENER_GRACE)
            unbound.value = !listener.connected.value
        }
    }

    private fun readAccesses() = Accesses(accesses.granted(), accesses.applicable())

    private fun state(read: Accesses, afterCallMinutes: Long, storageFailing: Boolean, listening: Boolean) =
        ProtectionState(
            read.granted,
            mayBeRestricted,
            afterCallMinutes,
            support,
            storageFailing,
            read.applicable,
            listening,
        )

    /** Read together: [AccessChecker.applicable] can change with the phone's settings too. */
    private data class Accesses(val granted: Set<Access>, val applicable: List<Access>)

    private companion object {
        /** A rotation keeps the subscription. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The system binds a listener in well under a second when it may (measured: Android 16, S24 FE, Redmi 9C). */
        val LISTENER_GRACE = 3.seconds
    }
}
