package com.filestech.callhalt.platform.stop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopAnswered
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.SensorQueue
import com.filestech.callhalt.platform.queueAsync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider

/**
 * The STOP notification swiped away, or cleared with the others, without being opened (Gemini review of 2026-11-07, 3;
 * FN-4): its STOP left without an answer, as by Home — it comes back once, and swiped again, the relative is told.
 * Android sends this only for a dismissal by the user: not when the notification is touched, nor when the app removes
 * it (a STOP screen shown). Not exported: only the notification's own intent reaches it.
 */
@AndroidEntryPoint
class StopNotificationSwiped : BroadcastReceiver() {

    @Inject lateinit var queue: SensorQueue

    @Inject lateinit var clock: PhoneClock

    /** Resolved in the queue, never at injection time: it reaches the database. */
    @Inject lateinit var processSignal: Provider<ProcessSignalUseCase>

    override fun onReceive(context: Context, intent: Intent) {
        // The phone's clocks at the dismissal: the time every signal carries.
        val at = clock.now()
        val triggers = triggersOf(intent)
        if (triggers.isEmpty()) return
        queueAsync(queue, "STOP notification swiped") {
            triggers.forEach { trigger -> processSignal.get()(StopAnswered(at, trigger, StopChoice.LEFT)) }
        }
    }

    companion object {
        private const val EXTRA_TRIGGERS = "com.filestech.callhalt.stop.SWIPED_TRIGGERS"

        /** The intent of the notification standing for [triggers]: what to answer "left" for, once swiped. */
        fun intent(context: Context, triggers: List<SensitiveActionKind>): Intent =
            Intent(context, StopNotificationSwiped::class.java)
                .putStringArrayListExtra(EXTRA_TRIGGERS, ArrayList(triggers.map { it.name }))

        /** Unknown names (an older version's) are dropped. */
        internal fun triggersOf(intent: Intent): List<SensitiveActionKind> =
            intent.getStringArrayListExtra(EXTRA_TRIGGERS).orEmpty()
                .mapNotNull { name -> SensitiveActionKind.entries.firstOrNull { it.name == name } }
    }
}
