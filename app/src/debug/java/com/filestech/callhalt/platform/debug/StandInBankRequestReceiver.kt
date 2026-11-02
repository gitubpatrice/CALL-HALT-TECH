package com.filestech.callhalt.platform.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.filestech.callhalt.platform.notification.BANK_REQUEST_CHANNELS

/**
 * DEBUG BUILDS ONLY (src/debug): posts a stand-in for a banking app's request to approve an operation, on the channel
 * the listener recognises, from this app. The whole path — notification access, listener, queue, engine, STOP — is
 * then measured on an emulator, which has no banking app; the real request is measured on a real phone.
 *
 *     adb shell am broadcast -a com.filestech.callhalt.debug.BANK_REQUEST --ei id 1 \
 *         -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.StandInBankRequestReceiver
 *
 * The same `id` again UPDATES the notification (what a banking app does to its own), another one posts a new one.
 * Reserved to the shell by `android:permission="android.permission.DUMP"`; on Android 13+, POST_NOTIFICATIONS is
 * granted from adb first (`pm grant`).
 */
class StandInBankRequestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Stand-in bank request (debug)", NotificationManager.IMPORTANCE_HIGH),
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Stand-in: approve an operation")
            .setAutoCancel(true)
            .build()
        manager.notify(intent.getIntExtra(EXTRA_ID, 1), notification)
    }

    private companion object {
        const val ACTION = "com.filestech.callhalt.debug.BANK_REQUEST"
        const val EXTRA_ID = "id"
        val CHANNEL = BANK_REQUEST_CHANNELS.first()
    }
}
