package com.iu.radioapp.di

import com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource
import com.iu.radioapp.data.remote.s1playout.PlayoutDataSource
import com.iu.radioapp.data.remote.s2archive.ArchiveDataSource
import com.iu.radioapp.data.remote.s2archive.FakeArchiveDataSource
import com.iu.radioapp.data.remote.s3requests.FakeRequestsDataSource
import com.iu.radioapp.data.remote.s3requests.RequestsDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeFeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeRatingEventDataSource
import com.iu.radioapp.data.remote.s4feedback.FeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.RatingEventDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlin.time.Clock

/** The in-memory fakes instead of HTTP; tests inject the fake types to steer them. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RemoteDataModule::class])
object TestRemoteDataModule {

    @Provides
    @Singleton
    fun provideFakePlayout(clock: Clock) = FakePlayoutDataSource(clock)

    @Provides
    fun providePlayout(fake: FakePlayoutDataSource): PlayoutDataSource = fake

    @Provides
    @Singleton
    fun provideFakeArchive() = FakeArchiveDataSource()

    @Provides
    fun provideArchive(fake: FakeArchiveDataSource): ArchiveDataSource = fake

    @Provides
    @Singleton
    fun provideFakeRequests() = FakeRequestsDataSource()

    @Provides
    fun provideRequests(fake: FakeRequestsDataSource): RequestsDataSource = fake

    @Provides
    @Singleton
    fun provideFakeFeedback(clock: Clock) = FakeFeedbackDataSource(clock)

    @Provides
    fun provideFeedback(fake: FakeFeedbackDataSource): FeedbackDataSource = fake

    @Provides
    @Singleton
    fun provideFakeRatingEvents() = FakeRatingEventDataSource()

    @Provides
    fun provideRatingEvents(fake: FakeRatingEventDataSource): RatingEventDataSource = fake
}
