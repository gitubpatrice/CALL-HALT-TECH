package com.filestech.callhalt.platform.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallEnded
import com.filestech.callhalt.domain.model.CallStarted
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.Signal
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.queueAsync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider

/**
 * DEBUG BUILDS ONLY (src/debug): a sensitive action injected from adb, to measure the STOP screen on a real phone
 * for an action no sensor observes yet (a code SMS, a remote-control app) — it served P-1: does it show over the
 * banking app? It goes through the same queue and the same engine as a real signal: the STOP shows only during a call
 * or just after it.
 *
 *     adb shell am broadcast -a com.filestech.callhalt.debug.SIGNAL --es kind CODE_MESSAGE \
 *         -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.DebugSignalReceiver
 *
 * `--es call START` (or `END`) in place of `kind`: the start or the end of a cellular call, for a phone with no SIM —
 * the Redmi of 2026-11-03, where the STOP's fallback is measured (D-024, P-21). No real call: "Hang up" has nothing
 * to end, and the STOP screen reads the phone's own state (no call going on).
 *
 * Reserved to the shell by `android:permission="android.permission.DUMP"` (src/debug/AndroidManifest.xml), a
 * permission no ordinary app can hold.
 */
@AndroidEntryPoint
class DebugSignalReceiver : BroadcastReceiver() {

    @Inject lateinit var queue: SensorQueue

    @Inject lateinit var processSignal: Provider<ProcessSignalUseCase>

    @Inject lateinit var clock: PhoneClock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val signal = signal(intent, clock.now()) ?: return
        queueAsync(queue, reason = "debug signal ${signal::class.simpleName}") { processSignal.get()(signal) }
    }

    private fun signal(intent: Intent, at: Moment): Signal? = when (intent.getStringExtra(EXTRA_CALL)) {
        "START" -> CallStarted(at, CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Hidden)
        "END" -> CallEnded(at, CallChannel.CELLULAR)
        else -> SensitiveActionKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
            ?.let { kind -> SensitiveAction(at, kind, source = "adb") }
    }

    private companion object {
        const val ACTION = "com.filestech.callhalt.debug.SIGNAL"
        const val EXTRA_KIND = "kind"
        const val EXTRA_CALL = "call"
    }
}
