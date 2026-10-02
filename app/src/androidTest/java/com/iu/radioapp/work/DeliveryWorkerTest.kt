package com.iu.radioapp.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.iu.radioapp.data.local.RadioDatabase
import com.iu.radioapp.data.local.TEST_EPOCH
import com.iu.radioapp.data.local.createInMemoryDatabase
import com.iu.radioapp.data.remote.s3requests.RequestsDataSource
import com.iu.radioapp.data.remote.s4feedback.FeedbackDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import com.iu.radioapp.repository.RatingRepository
import com.iu.radioapp.repository.SongRequestRepository
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto
import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingResponse
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Instant
import contract.s3requests.RequestStatus as RequestStatusDto

@RunWith(AndroidJUnit4::class)
class DeliveryWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = object : Clock {
        override fun now(): Instant = TEST_EPOCH
    }
    private val requests = ScriptedRequests()
    private val feedback = ScriptedFeedback()

    private lateinit var database: RadioDatabase
    private lateinit var songRequests: SongRequestRepository
    private lateinit var ratings: RatingRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        songRequests = SongRequestRepository(requests, database.outboxDao(), database.songRequestDao(), clock)
        ratings = RatingRepository(feedback, database.outboxDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun worker(): DeliveryWorker =
        TestListenableWorkerBuilder<DeliveryWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = DeliveryWorker(appContext, workerParameters, songRequests, ratings)
            })
            .build()

    private suspend fun outboxEntry(key: String) = database.outboxDao().findByIdempotencyKey(key)

    @Test
    fun deliveredRequestEndsTheRunSuccessfully() = runTest {
        songRequests.enqueue(songRequest("key-1"), displayName = null)

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(DeliveryStatus.DELIVERED, outboxEntry("key-1")?.status)
    }

    @Test
    fun serverFailureAsksForRetryAndCountsAnAttempt() = runTest {
        songRequests.enqueue(songRequest("key-1"), displayName = null)
        requests.answer = { Outcome.Error(Failure.Server) }

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(1, outboxEntry("key-1")?.attempts)
        assertEquals(DeliveryStatus.OPEN, outboxEntry("key-1")?.status)
    }

    @Test
    fun rejectionEndsTheRunSuccessfully() = runTest {
        songRequests.enqueue(songRequest("key-1"), displayName = null)
        requests.answer = { Outcome.Error(Failure.Rejected(reason = "Limit erreicht", retryable = false)) }

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(DeliveryStatus.REJECTED, outboxEntry("key-1")?.status)
    }

    @Test
    fun exceptionInOneRepositoryAsksForRetryAndTheOtherStillDelivers() = runTest {
        songRequests.enqueue(songRequest("key-1"), displayName = null)
        ratings.enqueue(rating("key-2"))
        requests.answer = { throw IllegalStateException("broken") }

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(1, outboxEntry("key-1")?.attempts)
        assertEquals(DeliveryStatus.DELIVERED, outboxEntry("key-2")?.status)
    }

    private fun songRequest(key: String) = SongRequest(
        idempotencyKey = key,
        requestId = null,
        trackId = "trk-1",
        trackTitle = "Sample Song",
        listenerId = "listener-1",
        message = null,
        createdAt = TEST_EPOCH,
        status = RequestStatus.PENDING,
        rejectionReason = null,
        scheduledBroadcast = null,
    )

    private fun rating(key: String) = Rating(
        idempotencyKey = key,
        ratingId = null,
        target = RatingTarget.PLAYLIST,
        referenceId = "show-1",
        value = 4,
        comment = null,
        createdAt = TEST_EPOCH,
        listenerId = "listener-1",
    )

    private class ScriptedRequests : RequestsDataSource {
        var answer: () -> Outcome<SongRequestResponse> = {
            Outcome.Success(SongRequestResponse(requestId = "req-1", status = RequestStatusDto.PENDING, scheduledBroadcast = null))
        }

        override suspend fun submitRequest(idempotencyKey: String, request: CreateSongRequestDto) = answer()

        override suspend fun getRequestStatus(requestId: String): Outcome<SongRequestStatusDto> =
            Outcome.Error(Failure.Server)

        override suspend fun getRequestsForListener(listenerId: String): Outcome<List<SongRequestOverviewDto>> =
            Outcome.Error(Failure.Server)
    }

    private class ScriptedFeedback : FeedbackDataSource {
        override suspend fun submitRating(idempotencyKey: String, rating: RatingRequest): Outcome<RatingResponse> =
            Outcome.Success(RatingResponse(ratingId = "rat-1"))

        override suspend fun getAggregate(showId: String): Outcome<AggregateDto> = Outcome.Error(Failure.Server)

        override suspend fun getRatingsSince(since: Instant, showId: String, token: String): Outcome<List<RatingEventDto>> =
            Outcome.Error(Failure.Server)
    }
}
