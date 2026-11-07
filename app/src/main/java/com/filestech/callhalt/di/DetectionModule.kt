package com.filestech.callhalt.di

import com.filestech.callhalt.data.settings.SettingsRepository
import com.filestech.callhalt.data.store.RoomDetectorStore
import com.filestech.callhalt.domain.detection.DetectionEngine
import com.filestech.callhalt.domain.port.DecisionExecutor
import com.filestech.callhalt.domain.port.DetectorStore
import com.filestech.callhalt.domain.port.LiveCallSource
import com.filestech.callhalt.domain.port.RestartSource
import com.filestech.callhalt.domain.port.SettingsSource
import com.filestech.callhalt.domain.usecase.ProcessSignalUseCase
import com.filestech.callhalt.platform.AndroidDecisionExecutor
import com.filestech.callhalt.platform.BootCountRestarts
import com.filestech.callhalt.platform.telephony.TelephonyLiveCall
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The domain's ports, bound to their Android implementations, and the one entry point every sensor feeds
 * ([ProcessSignalUseCase], reached through [com.filestech.callhalt.platform.SensorQueue] only).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionModule {

    @Binds
    abstract fun detectorStore(impl: RoomDetectorStore): DetectorStore

    @Binds
    abstract fun settingsSource(impl: SettingsRepository): SettingsSource

    @Binds
    abstract fun decisionExecutor(impl: AndroidDecisionExecutor): DecisionExecutor

    @Binds
    abstract fun restartSource(impl: BootCountRestarts): RestartSource

    @Binds
    abstract fun liveCallSource(impl: TelephonyLiveCall): LiveCallSource

    companion object {
        /** Pure and stateless: the domain module has no injection, so it is built here. */
        @Provides
        @Singleton
        fun detectionEngine(): DetectionEngine = DetectionEngine()

        /** One parameter per port of the use case: the domain module has no injection, so its constructor is called here. */
        @Provides
        @Singleton
        @Suppress("LongParameterList")
        fun processSignal(
            engine: DetectionEngine,
            store: DetectorStore,
            settings: SettingsSource,
            restarts: RestartSource,
            liveCall: LiveCallSource,
            executor: DecisionExecutor,
        ): ProcessSignalUseCase = ProcessSignalUseCase(engine, store, settings, restarts, liveCall, executor)
    }
}
