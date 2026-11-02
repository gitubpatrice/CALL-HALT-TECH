package com.filestech.callhalt.platform.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.filestech.callhalt.domain.model.SensitiveAction
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.queueAsync
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider

/**
 * DEBUG BUILDS ONLY (src/debug): a sensitive action injected from adb, to measure the STOP screen on a real phone
 * before the sensor that will observe it exists (P-1: does it show over the banking app?). It goes through the same
 * queue and the same engine as a real signal: the STOP shows only during a call or just after it.
 *
 *     adb shell am broadcast -a com.filestech.callhalt.debug.SIGNAL --es kind CODE_MESSAGE \
 *         -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.DebugSignalReceiver
 *
 * Reserved to the shell by `android:permission="android.permission.DUMP"` (src/debug/AndroidManifest.xml), a
 * permission no ordinary app can hold.
 */
@AndroidEntryPoint
class DebugSignalReceiver : BroadcastReceiver() {

    @Inject lateinit var queue: SensorQueue

    @Inject lateinit var processSignal: Provider<ProcessSignalUseCase>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val kind = SensitiveActionKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: return
        val at = Instant.now()
        queueAsync(queue, reason = "debug signal $kind") { processSignal.get()(SensitiveAction(at, kind, source = "adb")) }
    }

    private companion object {
        const val ACTION = "com.filestech.callhalt.debug.SIGNAL"
        const val EXTRA_KIND = "kind"
    }
}
