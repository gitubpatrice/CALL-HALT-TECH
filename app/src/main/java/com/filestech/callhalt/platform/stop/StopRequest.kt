package com.filestech.callhalt.platform.stop

import android.content.Intent
import android.os.Bundle
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * What one STOP screen shows, as the engine decided it ([com.filestech.callhalt.domain.model.Decision.ShowStop]):
 * the trigger, and the call it fell within — still going on or not, and on which channel. [id] lets the executor
 * know that THIS screen got through ([StopDisplay]).
 */
data class StopRequest(
    val id: Long,
    val trigger: SensitiveActionKind,
    val onCall: Boolean,
    val channel: CallChannel,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putLong(KEY_ID, id)
        putString(KEY_TRIGGER, trigger.name)
        putBoolean(KEY_ON_CALL, onCall)
        putString(KEY_CHANNEL, channel.name)
    }

    fun writeTo(intent: Intent): Intent = intent.putExtras(toBundle())

    companion object {
        private const val KEY_ID = "com.filestech.callhalt.stop.ID"
        private const val KEY_TRIGGER = "com.filestech.callhalt.stop.TRIGGER"
        private const val KEY_ON_CALL = "com.filestech.callhalt.stop.ON_CALL"
        private const val KEY_CHANNEL = "com.filestech.callhalt.stop.CHANNEL"

        /** Null for a bundle that does not carry a whole request: a screen that cannot say what happened shows nothing. */
        fun fromBundle(bundle: Bundle?): StopRequest? {
            if (bundle == null) return null
            val trigger = SensitiveActionKind.entries.firstOrNull { it.name == bundle.getString(KEY_TRIGGER) }
            val channel = CallChannel.entries.firstOrNull { it.name == bundle.getString(KEY_CHANNEL) }
            val complete = bundle.containsKey(KEY_ID) && bundle.containsKey(KEY_ON_CALL)
            return if (trigger != null && channel != null && complete) {
                StopRequest(bundle.getLong(KEY_ID), trigger, bundle.getBoolean(KEY_ON_CALL, true), channel)
            } else {
                null
            }
        }

        fun readFrom(intent: Intent): StopRequest? = fromBundle(intent.extras)
    }
}
