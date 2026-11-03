package com.filestech.callhalt.platform.notification

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the system has the protection's listener bound right now — not the same as the access granted
 * (docs/ARCHITECTURE.md, P-21, D-025). Measured on a Redmi 9C (MIUI 12, 2026-11-03): with MIUI's autostart refused,
 * once the app is stopped MIUI keeps the listener unbound ("Reject service"), even with the app opened again, while the
 * access still reads granted. Kept in this process: the home screen runs in it.
 */
@Singleton
class ListenerConnection @Inject constructor(@ApplicationContext private val context: Context) {

    private val bound = MutableStateFlow(false)

    val connected: StateFlow<Boolean> = bound.asStateFlow()

    /** From [NotificationListener]'s callbacks only. */
    internal fun connected(now: Boolean) {
        bound.value = now
    }

    /**
     * Asks the system to bind the listener again: its component turned off and back on, which the system answers by
     * binding it anew — the access kept (ListenerConnectionOnDeviceTest). Measured on the Redmi 9C, MIUI's autostart
     * refused, the app in front: bound 1.3 s after. `requestRebind` would not do: it binds only a listener unbound by
     * `requestUnbind` (AOSP's `ManagedServices.setComponentState`). Called with the app in front only, never while bound:
     * MIUI refuses an app in the background.
     */
    @Suppress("TooGenericExceptionCaught") // Whatever the phone answers: the protection is never worse for trying.
    fun rebind() {
        if (bound.value) return
        val listener = ComponentName(context, NotificationListener::class.java)
        val packages = context.packageManager
        Timber.i("Notification listener not bound: its component turned off and on")
        try {
            packages.setComponentEnabledSetting(listener, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL)
        } catch (refused: RuntimeException) {
            Timber.w(refused, "Notification listener not turned off")
        } finally {
            // Back on whatever happened: a listener left off could never be bound again (review of 2026-11-03, 5).
            packages.setComponentEnabledSetting(listener, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, DONT_KILL)
        }
    }

    private companion object {
        const val DONT_KILL = PackageManager.DONT_KILL_APP
    }
}
