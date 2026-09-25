package com.iu.radioapp.di

import com.iu.radioapp.BuildConfig
import com.iu.radioapp.data.remote.s1playout.HttpPlayoutDataSource
import com.iu.radioapp.data.remote.s1playout.PlayoutDataSource
import com.iu.radioapp.data.remote.s2archive.ArchiveDataSource
import com.iu.radioapp.data.remote.s2archive.HttpArchiveDataSource
import com.iu.radioapp.data.remote.s3requests.HttpRequestsDataSource
import com.iu.radioapp.data.remote.s3requests.RequestsDataSource
import com.iu.radioapp.data.remote.s4feedback.FeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.HttpFeedbackDataSource
import contract.common.RadioJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RemoteDataModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        // Every data source inspects the status code itself and maps it to a
        // Failure (see data/remote/common/HttpOutcome.kt); letting Ktor throw
        // on non-2xx would bypass that mapping entirely.
        expectSuccess = false
        install(ContentNegotiation) {
            json(RadioJson)
        }
        defaultRequest {
            url(BuildConfig.API_BASE_URL)
        }
    }

    @Provides
    @Singleton
    fun providePlayoutDataSource(client: HttpClient): PlayoutDataSource = HttpPlayoutDataSource(client)

    @Provides
    @Singleton
    fun provideArchiveDataSource(client: HttpClient): ArchiveDataSource = HttpArchiveDataSource(client)

    @Provides
    @Singleton
    fun provideRequestsDataSource(client: HttpClient): RequestsDataSource = HttpRequestsDataSource(client)

    @Provides
    @Singleton
    fun provideFeedbackDataSource(client: HttpClient): FeedbackDataSource = HttpFeedbackDataSource(client)
}
