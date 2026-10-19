package com.iu.radioapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.iu.radioapp.data.local.KeystoreTokenCipher
import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.PlaybackHistoryDao
import com.iu.radioapp.data.local.RadioDatabase
import com.iu.radioapp.data.local.SongRequestDao
import com.iu.radioapp.data.local.TokenCipher
import com.iu.radioapp.data.local.TrackCacheDao
import com.iu.radioapp.data.local.UserPreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/** Real Room and DataStore, but fresh for every test: in-memory database, new preferences file. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LocalDataModule::class])
internal object TestLocalDataModule {

    const val PREFERENCES_PREFIX = "journey-"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RadioDatabase =
        Room.inMemoryDatabaseBuilder(context, RadioDatabase::class.java).build()

    @Provides
    fun provideTrackCacheDao(database: RadioDatabase): TrackCacheDao = database.trackCacheDao()

    @Provides
    fun providePlaybackHistoryDao(database: RadioDatabase): PlaybackHistoryDao = database.playbackHistoryDao()

    @Provides
    fun provideOutboxDao(database: RadioDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideSongRequestDao(database: RadioDatabase): SongRequestDao = database.songRequestDao()

    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = KeystoreTokenCipher()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.cacheDir, "$PREFERENCES_PREFIX${UUID.randomUUID()}.preferences_pb")
        }

    @Provides
    @Singleton
    fun provideUserPreferencesDataSource(
        dataStore: DataStore<Preferences>,
        tokenCipher: TokenCipher,
    ): UserPreferencesDataSource = UserPreferencesDataSource(dataStore, tokenCipher)
}
