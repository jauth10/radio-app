package com.iu.radioapp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Real time that a test can move forward, e.g. past the five-minute staleness limit. */
class AdjustableClock : Clock {

    @Volatile
    private var offset = Duration.ZERO

    override fun now(): Instant = Clock.System.now() + offset

    fun advanceBy(duration: Duration) {
        offset += duration
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestClockModule {

    @Provides
    @Singleton
    fun provideAdjustableClock() = AdjustableClock()

    @Provides
    fun provideClock(clock: AdjustableClock): Clock = clock
}
