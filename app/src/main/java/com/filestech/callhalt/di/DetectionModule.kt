package com.filestech.callhalt.di

import com.filestech.callhalt.data.settings.SettingsRepository
import com.filestech.callhalt.data.store.RoomDetectorStore
import com.filestech.callhalt.domain.port.DetectorStore
import com.filestech.callhalt.domain.port.SettingsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The domain's ports, bound to their Android implementations. The engine and the use case are provided
 * here too once the first adapter feeds them, with the DecisionExecutor that carries their decisions
 * out (docs/ARCHITECTURE.md, § 6 — the next step).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionModule {

    @Binds
    abstract fun detectorStore(impl: RoomDetectorStore): DetectorStore

    @Binds
    abstract fun settingsSource(impl: SettingsRepository): SettingsSource
}
