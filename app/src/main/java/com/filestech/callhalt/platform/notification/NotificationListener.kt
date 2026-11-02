package com.filestech.callhalt.platform.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.SensorQueue
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider

/**
 * Sees the notifications of every app, once the user grants notification access (a restricted setting for an app
 * installed from a file, docs/ARCHITECTURE.md, P-3), and hands the ones [notificationSignal] recognises to the
 * [SensorQueue]. Of each notification it reads the app and the channel, nothing else: neither the title nor the
 * text, of a banking app's request or of anything else (PRIVACY.md).
 *
 * Bound by the system, which keeps the process alive while it is: no `goAsync()`, the queue's work runs in the
 * application's scope. The callbacks come on the main thread, where only that comparison runs.
 *
 * A notification UPDATED by its app is posted again: while the STOP it caused is unanswered, the engine takes the
 * repeat for the same request (NotificationSignalTest). Whether Sécur'Pass updates its own, once answered, is to
 * measure (docs/ARCHITECTURE.md, P-2).
 */
@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    /** Cheap to build: a channel and one coroutine. */
    @Inject lateinit var queue: SensorQueue

    /** Resolved in the queue, never at injection time: it reaches the database (Agenda Tech, audit F2). */
    @Inject lateinit var processSignal: Provider<ProcessSignalUseCase>

    override fun onListenerConnected() {
        // Debug builds only (no tree in release): for the measurements, when the system binds the listener.
        Timber.d("Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // The phone's clock at reception: the time every signal carries (Signal's contract).
        val at = Instant.now()
        val signal = notificationSignal(sbn.packageName, sbn.notification.channelId, at) ?: return
        Timber.d("Bank request from %s", sbn.packageName)
        queue.submit(reason = "notification ${signal.kind}") { processSignal.get()(signal) }
    }
}
