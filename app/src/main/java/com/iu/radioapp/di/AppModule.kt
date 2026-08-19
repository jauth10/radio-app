package com.iu.radioapp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Application-wide Hilt module. Intentionally empty apart from [ScaffoldMarker],
 * which serves as the injection proof for RAD-2. Database, DAOs and DataStore
 * follow in RAD-3, the network data sources in RAD-14.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideScaffoldMarker(): ScaffoldMarker =
        ScaffoldMarker("Hilt-Injektion aktiv")
}
