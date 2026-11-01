package com.filestech.callhalt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CallHaltApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Debug builds only. A release plants no tree, so no log line — which could name a number that
        // called — ever reaches logcat.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
