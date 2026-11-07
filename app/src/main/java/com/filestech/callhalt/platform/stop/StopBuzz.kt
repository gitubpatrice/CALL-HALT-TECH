package com.filestech.callhalt.platform.stop

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber

/**
 * Two short buzzes as a STOP opens (publication audit, U4; docs/ARCHITECTURE.md, D-036): the phone against the cheek,
 * its screen off, the user feels that something asks to be looked at. An alarm's vibration (`VIBRATE`, granted at
 * install, nothing asked): the view's haptic feedback, which needs no permission, follows the setting for touch
 * vibrations — off on the S9 (measured, 2026-11-07), and no app may ignore it from Android 13. Nothing on a phone
 * without a vibrator. An aid only: whatever the vibrator throws (a maker's service, the permission taken back by a
 * work profile) is caught, and the STOP shows all the same (review of 2026-11-07).
 */
@Suppress("TooGenericExceptionCaught") // Anything the vibrator throws must not take the STOP screen down with it.
internal fun Context.buzzForStop() {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        val effect = VibrationEffect.createWaveform(BUZZ, NO_REPEAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    } catch (failed: RuntimeException) {
        Timber.w(failed, "STOP buzz failed")
    }
}

private const val ON_MILLIS = 250L
private const val BETWEEN_MILLIS = 150L

/** At once, on 250 ms, off 150 ms, on 250 ms: felt against the cheek, over before the eyes reach the screen. */
private val BUZZ = longArrayOf(0, ON_MILLIS, BETWEEN_MILLIS, ON_MILLIS)
private const val NO_REPEAT = -1
