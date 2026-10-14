package com.iu.radioapp.di

import android.util.Log
import com.iu.radioapp.ui.host.DisplayLatencyRecorder
import com.iu.radioapp.ui.host.LatencyRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock

/** Debug build: latency lines go to Logcat under [DisplayLatencyRecorder.TAG] (RAD-18). */
@Module
@InstallIn(SingletonComponent::class)
object LatencyModule {

    @Provides
    @Singleton
    fun provideLatencyRecorder(clock: Clock): LatencyRecorder =
        DisplayLatencyRecorder(clock) { Log.i(DisplayLatencyRecorder.TAG, it) }
}
