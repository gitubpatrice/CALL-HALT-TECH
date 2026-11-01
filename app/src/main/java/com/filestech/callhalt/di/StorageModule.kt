package com.filestech.callhalt.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.filestech.callhalt.data.store.CallHaltDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * No `fallbackToDestructiveMigration`, ever: a missing migration must never erase the journal a
     * relative relies on. It throws instead — caught by a migration test before any release, and at run time
     * by RoomDetectorStore, which then decides from memory; that degraded state must show on the protection
     * status screen (docs/ARCHITECTURE.md, P-10). Migrations are additive only (D-004).
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CallHaltDatabase =
        Room.databaseBuilder(context, CallHaltDatabase::class.java, CallHaltDatabase.NAME).build()

    /**
     * A corrupted settings file is replaced by an empty one, which reads as the PROTECTIVE defaults
     * (protection on): failing on the protective side, never "off" by accident.
     */
    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
    )

    private const val SETTINGS_FILE = "settings"
}
