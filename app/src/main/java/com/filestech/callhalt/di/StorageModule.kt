package com.filestech.callhalt.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
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

    /** Each commit on the disk, no destructive migration: [CallHaltDatabase.create] says why. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CallHaltDatabase = CallHaltDatabase.create(context)

    /**
     * A corrupted settings file is replaced by an empty one, which reads as the PROTECTIVE defaults
     * (protection on): failing on the protective side, never "off" by accident.
     */
    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = preferencesStore(context, scope, SETTINGS_FILE)

    /** A corrupted file reads as "nothing recorded": the next call is still reported, its direction unknown. */
    @Provides
    @Singleton
    @PhoneStateStore
    fun phoneStateDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = preferencesStore(context, scope, PHONE_STATE_FILE)

    private fun preferencesStore(context: Context, scope: CoroutineScope, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { context.preferencesDataStoreFile(name) },
        )

    private const val SETTINGS_FILE = "settings"
    private const val PHONE_STATE_FILE = "phone_state"
}
