package com.filestech.callhalt.platform.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.queueAsync
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

/**
 * Receives the phone's call state (docs/ARCHITECTURE.md, § 6): declared in the manifest, it is woken even when
 * the app is not running — Android exempts PHONE_STATE from the ban on implicit broadcasts. It needs
 * READ_PHONE_STATE, without which the system sends it nothing.
 *
 * EXPORTED, on purpose: PHONE_STATE is a protected broadcast — measured on the S9 (Samsung, Android 10), even the
 * adb shell is refused ("not allowed to send broadcast"), explicitly addressed or not — so no app can forge it,
 * while a phone that sends it from its telephony process rather than from the system is still heard (a
 * non-exported receiver accepts the system alone). Any other action reaching it is ignored.
 */
@AndroidEntryPoint
class CallStateReceiver : BroadcastReceiver() {

    /** Cheap to build: a channel and one coroutine. */
    @Inject lateinit var queue: SensorQueue

    @Inject lateinit var clock: PhoneClock

    /** Resolved in the queue, never at injection time: it reaches the database (see [queueAsync]). */
    @Inject lateinit var sensor: Provider<CallStateSensor>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = PhoneState.fromExtra(intent.getStringExtra(TelephonyManager.EXTRA_STATE)) ?: return
        // The phone's clocks at reception: the time every signal carries (Signal's contract).
        val at = clock.now()
        // Debug builds only: which SIM, for the dual-SIM measurement (docs/ARCHITECTURE.md, § 6). Never the number.
        Timber.d("PHONE_STATE %s, subscription %d", state, intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1))
        queueAsync(queue, reason = "phone state ${state.name}") { sensor.get().onPhoneState(state, at) }
    }
}
