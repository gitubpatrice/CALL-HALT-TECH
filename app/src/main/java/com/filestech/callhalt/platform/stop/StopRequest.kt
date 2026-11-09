package com.filestech.callhalt.platform.stop

import android.content.Intent
import android.os.Bundle
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * What one STOP screen shows, as the engine decided it ([com.filestech.callhalt.domain.model.Decision.ShowStop]):
 * the trigger, and the call it fell within — still going on or not, and on which channel. [id] lets the executor
 * know that THIS screen got through ([StopDisplay]). [earlier]: the triggers of a STOP notification still waiting to be
 * touched, in their order of arrival — the screen this request opens stands for them too, and speaks of the gravest
 * (publication audit, N-1): a notification replaced by a lesser one must not lose its way out. [installedApp]: the
 * remote-control app whose installation this STOP is about (a package of the list), which it offers to uninstall
 * (FN-3, D-039); null for any other STOP, and for one coming back (the engine no longer knows it).
 */
data class StopRequest(
    val id: Long,
    val trigger: SensitiveActionKind,
    val onCall: Boolean,
    val channel: CallChannel,
    val earlier: List<SensitiveActionKind> = emptyList(),
    val installedApp: String? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putLong(KEY_ID, id)
        putString(KEY_TRIGGER, trigger.name)
        putBoolean(KEY_ON_CALL, onCall)
        putString(KEY_CHANNEL, channel.name)
        putStringArray(KEY_EARLIER, earlier.map { it.name }.toTypedArray())
        putString(KEY_INSTALLED_APP, installedApp)
    }

    fun writeTo(intent: Intent): Intent = intent.putExtras(toBundle())

    companion object {
        private const val KEY_ID = "com.filestech.callhalt.stop.ID"
        private const val KEY_TRIGGER = "com.filestech.callhalt.stop.TRIGGER"
        private const val KEY_ON_CALL = "com.filestech.callhalt.stop.ON_CALL"
        private const val KEY_CHANNEL = "com.filestech.callhalt.stop.CHANNEL"
        private const val KEY_EARLIER = "com.filestech.callhalt.stop.EARLIER"
        private const val KEY_INSTALLED_APP = "com.filestech.callhalt.stop.INSTALLED_APP"

        /** Null for a bundle that does not carry a whole request: a screen that cannot say what happened shows nothing. */
        fun fromBundle(bundle: Bundle?): StopRequest? {
            if (bundle == null) return null
            val trigger = SensitiveActionKind.entries.firstOrNull { it.name == bundle.getString(KEY_TRIGGER) }
            val channel = CallChannel.entries.firstOrNull { it.name == bundle.getString(KEY_CHANNEL) }
            val complete = bundle.containsKey(KEY_ID) && bundle.containsKey(KEY_ON_CALL)
            // In their saved order; an unknown name is dropped, not guessed.
            val earlier = bundle.getStringArray(KEY_EARLIER).orEmpty()
                .mapNotNull { name -> SensitiveActionKind.entries.firstOrNull { it.name == name } }
            return if (trigger != null && channel != null && complete) {
                StopRequest(
                    id = bundle.getLong(KEY_ID),
                    trigger = trigger,
                    onCall = bundle.getBoolean(KEY_ON_CALL, true),
                    channel = channel,
                    earlier = earlier,
                    installedApp = bundle.getString(KEY_INSTALLED_APP),
                )
            } else {
                null
            }
        }

        fun readFrom(intent: Intent): StopRequest? = fromBundle(intent.extras)
    }
}
