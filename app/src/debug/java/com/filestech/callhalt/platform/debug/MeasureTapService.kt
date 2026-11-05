package com.filestech.callhalt.platform.debug

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * DEBUG BUILDS ONLY (src/debug): the P-15 measurement (docs/ARCHITECTURE.md, § 7). Can an accessibility service — the
 * kind a remote-control app has the victim turn on to take the phone in hand (RustDesk's "Input control") — press
 * "Continue" on the STOP screen? This one is what such an app is to Android: not a declared accessibility tool
 * (`isAccessibilityTool` left false), allowed to perform gestures. It does nothing by itself: a short tap where adb says,
 * logged with how Android answered. The same tap on another app's button is the positive control.
 *
 *     adb shell settings put secure enabled_accessibility_services \
 *         com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.MeasureTapService
 *     adb shell am broadcast -a com.filestech.callhalt.debug.TAP --ei x 540 --ei y 2211 \
 *         -n com.filestech.callhalt.debug/com.filestech.callhalt.platform.debug.MeasureTapReceiver
 */
class MeasureTapService : AccessibilityService() {

    override fun onServiceConnected() {
        connected = this
        Timber.i("P-15 tap service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun tap(x: Float, y: Float) {
        val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, TAP_MS)
        val sent = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Timber.i("P-15 tap at %s,%s completed", x, y)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Timber.i("P-15 tap at %s,%s cancelled", x, y)
                }
            },
            null,
        )
        Timber.i("P-15 tap at %s,%s sent: %s", x, y, sent)
    }

    companion object {
        /** A tap, not a press: well under the long-press timeout. */
        private const val TAP_MS = 50L

        /** Main thread only: set and read in the service's and the receiver's callbacks. */
        var connected: MeasureTapService? = null
            private set
    }
}

/** The adb command of [MeasureTapService]. Reserved to the shell by DUMP (src/debug/AndroidManifest.xml). */
class MeasureTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.filestech.callhalt.debug.TAP") return
        val service = MeasureTapService.connected ?: return Timber.w("P-15 tap service not connected")
        service.tap(intent.getIntExtra("x", 0).toFloat(), intent.getIntExtra("y", 0).toFloat())
    }
}
