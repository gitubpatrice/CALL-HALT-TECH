package com.filestech.callhalt.di

import com.filestech.callhalt.platform.AndroidPhoneClock
import com.filestech.callhalt.platform.PhoneClock
import com.filestech.callhalt.platform.ProcessWakeUps
import com.filestech.callhalt.platform.WakeUps
import com.filestech.callhalt.platform.access.AccessChecker
import com.filestech.callhalt.platform.access.AndroidAccessChecker
import com.filestech.callhalt.platform.stop.ActivityScreenStarter
import com.filestech.callhalt.platform.stop.ActivityStopLauncher
import com.filestech.callhalt.platform.stop.CallControl
import com.filestech.callhalt.platform.stop.ScreenStarter
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.stop.StopNotice
import com.filestech.callhalt.platform.stop.StopNotifier
import com.filestech.callhalt.platform.stop.TelecomCallControl
import com.filestech.callhalt.platform.warning.WarningNotifier
import com.filestech.callhalt.platform.warning.WarningPoster
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What the platform layer reaches through an interface, so that the logic around it is tested on the JVM. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    abstract fun stopLauncher(impl: ActivityStopLauncher): StopLauncher

    @Binds
    abstract fun screenStarter(impl: ActivityScreenStarter): ScreenStarter

    @Binds
    abstract fun stopNotice(impl: StopNotifier): StopNotice

    @Binds
    abstract fun warningPoster(impl: WarningNotifier): WarningPoster

    @Binds
    abstract fun callControl(impl: TelecomCallControl): CallControl

    @Binds
    abstract fun phoneClock(impl: AndroidPhoneClock): PhoneClock

    @Binds
    abstract fun wakeUps(impl: ProcessWakeUps): WakeUps

    @Binds
    abstract fun accessChecker(impl: AndroidAccessChecker): AccessChecker
}
