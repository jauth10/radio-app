package com.iu.radioapp.repository

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.data.remote.s4feedback.FakeFeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeRatingEventDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RatingWithDelivery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class RatingRepositoryTest {

    private val clock = MutableClock()
    private val feedback = FakeFeedbackDataSource(clock)
    private val store = InMemoryLocalStore()
    private val outboxDao = FakeOutboxDao(store)
    private val repository = RatingRepository(feedback, FakeRatingEventDataSource(), outboxDao, clock)

    private suspend fun entry(key: String = "key-1") = outboxDao.findByIdempotencyKey(key)

    @Test
    fun `enqueue creates an open rating entry and no request row`() = runTest {
        repository.enqueue(rating())

        assertEquals(DeliveryStatus.OPEN, entry()?.status)
        assertEquals(OperationType.RATING, entry()?.operation)
        assertTrue(store.songRequests.value.isEmpty())
    }

    @Test
    fun `delivery marks the entry delivered`() = runTest {
        repository.enqueue(rating())

        assertEquals(Outcome.Success(Unit), repository.deliverOpen())

        assertEquals(DeliveryStatus.DELIVERED, entry()?.status)
    }

    @Test
    fun `already rated is a rejection with its reason and is not sent again`() = runTest {
        repository.enqueue(rating("key-1"))
        repository.deliverOpen()
        repository.enqueue(rating("key-2"))

        assertEquals(Outcome.Success(Unit), repository.deliverOpen())
        feedback.nextFailure = Failure.Server
        repository.deliverOpen()

        assertEquals(DeliveryStatus.REJECTED, entry("key-2")?.status)
        assertEquals("already rated", entry("key-2")?.rejectionReason)
        assertEquals(Failure.Server, feedback.nextFailure)
    }

    @Test
    fun `value out of range is a rejection`() = runTest {
        repository.enqueue(rating(value = 6))

        repository.deliverOpen()

        assertEquals(DeliveryStatus.REJECTED, entry()?.status)
        assertEquals("value must be 1..5", entry()?.rejectionReason)
    }

    @Test
    fun `server failure counts an attempt and keeps the entry open`() = runTest {
        repository.enqueue(rating())
        feedback.nextFailure = Failure.Server

        assertEquals(Outcome.Error(Failure.Server), repository.deliverOpen())

        assertEquals(DeliveryStatus.OPEN, entry()?.status)
        assertEquals(1, entry()?.attempts)
    }

    @Test
    fun `fifth connection failure marks the entry failed and retry reopens it`() = runTest {
        repository.enqueue(rating())
        feedback.nextFailure = Failure.Connection
        feedback.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }
        assertEquals(DeliveryStatus.FAILED, entry()?.status)
        assertNull(entry()?.rejectionReason)

        repository.retry("key-1")
        repository.deliverOpen()

        assertEquals(DeliveryStatus.DELIVERED, entry()?.status)
    }

    @Test
    fun `song request entries are left to their own repository`() = runTest {
        repository.enqueue(rating("key-r"))
        val songRequestEntryId = outboxDao.insert(
            OutboxEntity(
                idempotencyKey = "key-s",
                operation = OperationType.SONG_REQUEST,
                payload = "{}",
                attempts = 0,
                lastAttemptAt = null,
                status = DeliveryStatus.OPEN,
                rejectionReason = null,
            )
        )

        repository.deliverOpen()

        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(songRequestEntryId)?.status)
        assertEquals(listOf("key-r"), repository.observeDeliveries().firstValue().map { it.idempotencyKey })
    }

    @Test
    fun `getAggregate maps the station aggregate`() = runTest {
        val aggregate = (repository.getAggregate("show-1") as Outcome.Success).value

        assertEquals(1, aggregate.playlistRatingCount)
        assertEquals(4.0, aggregate.averagePlaylistRating, 0.0)
        assertEquals(Instant.parse("2026-08-28T09:00:00Z"), aggregate.windowStart)
    }

    @Test
    fun `getAggregate passes unauthorized on`() = runTest {
        feedback.nextFailure = Failure.Unauthorized

        assertEquals(Outcome.Error(Failure.Unauthorized), repository.getAggregate("show-1"))
    }

    @Test
    fun `getRatingsSince maps the events`() = runTest {
        val events =
            (repository.getRatingsSince(Instant.parse("2026-08-28T09:15:00Z"), "show-1", "session-1") as Outcome.Success).value

        assertEquals(listOf("rat-2"), events.map { it.ratingId })
        assertEquals(RatingTarget.HOST, events.single().target)
    }

    @Test
    fun `getRatingsSince passes a connection failure on`() = runTest {
        feedback.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), repository.getRatingsSince(TEST_NOW, "show-1", "session-1"))
    }

    @Test
    fun `an entry queued before the display name existed is still delivered`() = runTest {
        outboxDao.insert(
            OutboxEntity(
                idempotencyKey = "key-old",
                operation = OperationType.RATING,
                payload = """{"target":"PLAYLIST","referenceId":"show-1","value":4,"listenerId":"listener-1","timestamp":"2026-09-20T12:00:00Z"}""",
                attempts = 0,
                lastAttemptAt = null,
                status = DeliveryStatus.OPEN,
                rejectionReason = null,
            )
        )

        assertEquals(Outcome.Success(Unit), repository.deliverOpen())

        assertEquals(DeliveryStatus.DELIVERED, entry("key-old")?.status)
    }

    @Test
    fun `failed ratings are read back from their payload, newest first, without open or song request entries`() = runTest {
        repository.enqueue(rating("key-a", value = 2))
        repository.enqueue(rating("key-b", value = 5, target = RatingTarget.HOST, referenceId = "host-1"))
        // A server fault, unlike a lost connection, does not stop the run, so both entries use up their attempts.
        feedback.nextFailure = Failure.Server
        feedback.failureRepeatCount = 10
        repeat(5) { repository.deliverOpen() }
        repository.enqueue(rating("key-open"))
        outboxDao.insert(
            OutboxEntity(
                idempotencyKey = "key-s",
                operation = OperationType.SONG_REQUEST,
                payload = "{}",
                attempts = 5,
                lastAttemptAt = null,
                status = DeliveryStatus.FAILED,
                rejectionReason = null,
            )
        )

        val failed = repository.observeUndeliveredRatings().firstValue()

        val hostRating = rating("key-b", value = 5, target = RatingTarget.HOST, referenceId = "host-1")
        assertEquals(listOf(hostRating, rating("key-a", value = 2)), failed.map { it.rating })
        assertTrue(failed.all { it.status == DeliveryStatus.FAILED && it.rejectionReason == null })
    }

    @Test
    fun `rejected ratings are listed with their reason beside the failed ones`() = runTest {
        repository.enqueue(rating("key-f"))
        feedback.nextFailure = Failure.Server
        feedback.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }
        repository.enqueue(rating("key-r", value = 6))
        repository.deliverOpen()
        repository.enqueue(rating("key-d"))
        repository.deliverOpen()

        assertEquals(
            listOf(
                RatingWithDelivery(rating("key-r", value = 6), DeliveryStatus.REJECTED, "value must be 1..5"),
                RatingWithDelivery(rating("key-f"), DeliveryStatus.FAILED, null),
            ),
            repository.observeUndeliveredRatings().firstValue(),
        )
    }

    @Test
    fun `dismissing removes a rejected rating and leaves a failed one alone`() = runTest {
        repository.enqueue(rating("key-f"))
        feedback.nextFailure = Failure.Server
        feedback.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }
        repository.enqueue(rating("key-r", value = 6))
        repository.deliverOpen()

        assertEquals(Outcome.Success(Unit), repository.dismissRejected("key-r"))
        assertEquals(Outcome.Success(Unit), repository.dismissRejected("key-f"))

        assertNull(entry("key-r"))
        assertEquals(listOf("key-f"), repository.observeUndeliveredRatings().firstValue().map { it.rating.idempotencyKey })
    }

    @Test
    fun `an unreadable failed payload is left out instead of failing the whole list`() = runTest {
        outboxDao.insert(
            OutboxEntity(
                idempotencyKey = "key-broken",
                operation = OperationType.RATING,
                payload = "{",
                attempts = 5,
                lastAttemptAt = null,
                status = DeliveryStatus.FAILED,
                rejectionReason = null,
            )
        )
        repository.enqueue(rating("key-a"))
        feedback.nextFailure = Failure.Server
        feedback.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }

        assertEquals(
            Outcome.Success(listOf(RatingWithDelivery(rating("key-a"), DeliveryStatus.FAILED, null))),
            repository.observeUndeliveredRatings().first(),
        )
    }
}
