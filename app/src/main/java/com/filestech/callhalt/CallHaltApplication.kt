package com.filestech.callhalt

import android.app.Application
import com.filestech.callhalt.platform.power.ShutdownSensor
import com.filestech.callhalt.platform.stop.StopNotifier
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CallHaltApplication : Application() {

    @Inject lateinit var stopNotifier: StopNotifier

    /** Cheap to build: the queue, the clock, and a provider resolved only when the phone shuts down. */
    @Inject lateinit var shutdownSensor: ShutdownSensor

    override fun onCreate() {
        super.onCreate()
        // Debug builds only. A release plants no tree, so no log line — which could name a number that
        // called — ever reaches logcat.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Before any STOP: a notification on a channel that does not exist is not shown.
        stopNotifier.createChannel()
        // At every start of the process: Android tells only the receivers registered at run time (D-028).
        shutdownSensor.register(this)
    }
}
