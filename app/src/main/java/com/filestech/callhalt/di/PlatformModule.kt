package com.filestech.callhalt.di

import com.filestech.callhalt.platform.AndroidBootReader
import com.filestech.callhalt.platform.BootReader
import com.filestech.callhalt.platform.stop.ActivityStopLauncher
import com.filestech.callhalt.platform.stop.CallControl
import com.filestech.callhalt.platform.stop.StopLauncher
import com.filestech.callhalt.platform.stop.TelecomCallControl
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
    abstract fun callControl(impl: TelecomCallControl): CallControl

    @Binds
    abstract fun bootReader(impl: AndroidBootReader): BootReader
}
