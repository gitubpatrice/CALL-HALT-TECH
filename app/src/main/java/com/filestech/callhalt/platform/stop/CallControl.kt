package com.filestech.callhalt.platform.stop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Ends the call in progress, when the app may. */
interface CallControl {
    /** The app holds the permission to end a call (ANSWER_PHONE_CALLS). */
    fun canEndCall(): Boolean

    /**
     * True only when the phone says, right now, that no call goes on. False when a call goes on — or when the app
     * cannot ask: then the screen keeps what the engine decided.
     */
    fun noCallGoingOn(): Boolean

    /** True when the call was ended. */
    fun endCall(): Boolean
}

/**
 * Through [TelecomManager.endCall]: deprecated since Android 10 but still effective for an app holding
 * ANSWER_PHONE_CALLS, on a cellular call — not a VOIP one, nor an emergency call (docs/ARCHITECTURE.md, § 6).
 */
@Singleton
class TelecomCallControl @Inject constructor(@ApplicationContext private val context: Context) : CallControl {

    override fun canEndCall(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    /** [TelecomManager.isInCall]: any call, VOIP ones placed through the system's call service included. */
    override fun noCallGoingOn(): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (telecom == null || !granted) return false
        return try {
            !telecom.isInCall
        } catch (refused: SecurityException) {
            Timber.w(refused, "Call state not readable")
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun endCall(): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED
        if (telecom == null || !granted) return false
        return try {
            telecom.endCall()
        } catch (refused: SecurityException) {
            // The permission withdrawn between the check and the call.
            Timber.w(refused, "Call not ended")
            false
        }
    }
}
