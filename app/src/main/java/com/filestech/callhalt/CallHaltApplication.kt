package com.filestech.callhalt

import android.app.Application
import com.filestech.callhalt.platform.accessibility.AccessibilitySensor
import com.filestech.callhalt.platform.install.InstallSensor
import com.filestech.callhalt.platform.power.ShutdownSensor
import com.filestech.callhalt.platform.stop.StopNotifier
import com.filestech.callhalt.platform.telephony.CallStateAtStart
import com.filestech.callhalt.platform.warning.WarningNotifier
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CallHaltApplication : Application() {

    @Inject lateinit var stopNotifier: StopNotifier

    @Inject lateinit var warningNotifier: WarningNotifier

    /** Cheap to build: the queue, the clock, and a provider resolved only when the phone shuts down. */
    @Inject lateinit var shutdownSensor: ShutdownSensor

    /** As cheap: built the same way, resolved only when a remote-control app is installed. */
    @Inject lateinit var installSensor: InstallSensor

    /** As cheap: resolved only when an app is given the accessibility access. */
    @Inject lateinit var accessibilitySensor: AccessibilitySensor

    /** As cheap: the queue, the clock, the phone's call state, and a provider resolved in the queue. */
    @Inject lateinit var callStateAtStart: CallStateAtStart

    override fun onCreate() {
        super.onCreate()
        // Debug builds only. A release plants no tree, so no log line — which could name a number that
        // called — ever reaches logcat.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Before any STOP: a notification on a channel that does not exist is not shown.
        stopNotifier.createChannel()
        warningNotifier.createChannel()
        // At every start of the process: Android tells only the receivers registered at run time (D-028, D-031).
        shutdownSensor.register(this)
        installSensor.register(this)
        // And the accessibility services turned on, from what they are now (D-033).
        accessibilitySensor.register(this)
        // And the call going on now, which no broadcast may have told: answered before the first unlock (FN-1, P-7).
        callStateAtStart.check()
    }
}
