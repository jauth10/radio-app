package com.iu.radioapp.di

import com.iu.radioapp.ui.host.LatencyRecorder
import com.iu.radioapp.ui.host.NoLatencyRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release build: no latency measurement (RAD-18). */
@Module
@InstallIn(SingletonComponent::class)
object LatencyModule {

    @Provides
    fun provideLatencyRecorder(): LatencyRecorder = NoLatencyRecorder
}
