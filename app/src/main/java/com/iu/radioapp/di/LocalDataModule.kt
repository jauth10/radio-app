package com.iu.radioapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object LocalDataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RadioDatabase =
        Room.databaseBuilder(context, RadioDatabase::class.java, RadioDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideTrackCacheDao(database: RadioDatabase): TrackCacheDao = database.trackCacheDao()

    @Provides
    @Singleton
    fun providePlaybackHistoryDao(database: RadioDatabase): PlaybackHistoryDao =
        database.playbackHistoryDao()

    @Provides
    @Singleton
    fun provideOutboxDao(database: RadioDatabase): OutboxDao = database.outboxDao()

    @Provides
    @Singleton
    fun provideSongRequestDao(database: RadioDatabase): SongRequestDao = database.songRequestDao()

    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = KeystoreTokenCipher()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(UserPreferencesDataSource.STORE_NAME)
        }

    @Provides
    @Singleton
    fun provideUserPreferencesDataSource(
        dataStore: DataStore<Preferences>,
        tokenCipher: TokenCipher,
    ): UserPreferencesDataSource = UserPreferencesDataSource(dataStore, tokenCipher)
}
