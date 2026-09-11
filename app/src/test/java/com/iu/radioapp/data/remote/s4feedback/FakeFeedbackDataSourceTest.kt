package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FakeFeedbackDataSourceTest {

    private val fixedNow = Instant.parse("2026-08-28T12:00:00Z")
    private val fake = FakeFeedbackDataSource(clock = object : Clock {
        override fun now() = fixedNow
    })

    private fun sampleRating(
        listenerId: String = "listener-1",
        target: RatingTarget = RatingTarget.PLAYLIST,
        referenceId: String = FakeFeedbackDataSource.SEEDED_SHOW_ID,
        value: Int = 4,
    ) = RatingRequest(
        target = target,
        referenceId = referenceId,
        value = value,
        comment = null,
        listenerId = listenerId,
        timestamp = fixedNow,
    )

    @Test
    fun `submitRating succeeds and is reflected in the aggregate for its show`() = runTest {
        fake.submitRating("key-1", sampleRating())

        val aggregate = (fake.getAggregate(FakeFeedbackDataSource.SEEDED_SHOW_ID) as Outcome.Success).value
        assertEquals(2, aggregate.playlistRatingCount)
    }

    @Test
    fun `getAggregate for an unrelated show does not include this show's ratings`() = runTest {
        val aggregate = (fake.getAggregate("show-unrelated") as Outcome.Success).value
        assertEquals(0, aggregate.playlistRatingCount)
        assertEquals(0, aggregate.hostRatingCount)
    }

    @Test
    fun `submitRating rejects a value outside 1 to 5`() = runTest {
        val outcome = fake.submitRating("key-2", sampleRating(value = 6))
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `submitRating rejects a second rating from the same listener for the same target`() = runTest {
        fake.submitRating("key-3", sampleRating(listenerId = "listener-2"))
        val outcome = fake.submitRating("key-4", sampleRating(listenerId = "listener-2"))
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `getRatingsSince only returns ratings received after the given instant`() = runTest {
        val since = Instant.parse("2026-08-28T09:15:00Z")
        val outcome = fake.getRatingsSince(since, FakeFeedbackDataSource.SEEDED_SHOW_ID) as Outcome.Success
        assertEquals(1, outcome.value.size)
        assertEquals("rat-2", outcome.value.first().ratingId)
    }

    @Test
    fun `submitRating returns Connection failure when set`() = runTest {
        fake.nextFailure = Failure.Connection
        assertEquals(Failure.Connection, (fake.submitRating("key-5", sampleRating()) as Outcome.Error).failure)
    }

    @Test
    fun `submitRating returns Server failure when set`() = runTest {
        fake.nextFailure = Failure.Server
        assertEquals(Failure.Server, (fake.submitRating("key-6", sampleRating()) as Outcome.Error).failure)
    }

    @Test
    fun `submitRating returns Unauthorized failure when set`() = runTest {
        fake.nextFailure = Failure.Unauthorized
        assertEquals(Failure.Unauthorized, (fake.submitRating("key-7", sampleRating()) as Outcome.Error).failure)
    }

    @Test
    fun `failureRepeatCount fails that many calls in a row, then succeeds`() = runTest {
        fake.nextFailure = Failure.Server
        fake.failureRepeatCount = 2

        assertTrue(fake.submitRating("key-8", sampleRating()) is Outcome.Error)
        assertTrue(fake.submitRating("key-9", sampleRating()) is Outcome.Error)
        assertTrue(fake.submitRating("key-10", sampleRating()) is Outcome.Success)
    }
}
