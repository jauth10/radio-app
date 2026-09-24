package com.iu.radioapp.interactor

import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.domain.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongRequestInteractorTest {

    private val fixture = InteractorFixture()
    private val interactor = fixture.songRequestInteractor
    private val repository = fixture.songRequestRepository
    private val outboxDao = fixture.outboxDao
    private val scheduler = fixture.scheduler

    private suspend fun submit(track: Track = track(broadcastable = true), message: String? = null): SongRequest =
        (interactor.submitRequest(track, message) as Submission.Queued).value

    private suspend fun entry(key: String) = outboxDao.findByIdempotencyKey(key)

    private suspend fun shown() = interactor.observeRequests().first()

    @Test
    fun `submit queues the request with an open outbox entry and schedules delivery`() = runTest {
        val request = submit()

        assertEquals(RequestStatus.PENDING, request.status)
        assertNull(request.requestId)
        assertEquals("Sample Song", request.trackTitle)
        assertEquals(DeliveryStatus.OPEN, entry(request.idempotencyKey)?.status)
        assertEquals(1, scheduler.calls)
        val row = shown().single()
        assertEquals(request.idempotencyKey, row.request.idempotencyKey)
        assertEquals(DeliveryStatus.OPEN, row.deliveryStatus)
    }

    @Test
    fun `display name and message travel with the request`() = runTest {
        fixture.listenerRepository.setDisplayName("Jo")

        val request = submit(message = "  ")
        val named = submit(message = "Bitte laut")

        assertNull(request.message)
        assertEquals("Bitte laut", named.message)
        assertTrue(entry(named.idempotencyKey)?.payload?.contains("\"displayName\":\"Jo\"") == true)
    }

    @Test
    fun `every submission gets its own idempotency key`() = runTest {
        val first = submit()
        val second = submit()

        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals(2, outboxDao.openEntries().size)
    }

    @Test
    fun `key stays stable through failed attempts, manual retry and delivery`() = runTest {
        val request = submit()
        fixture.requests.nextFailure = Failure.Server
        fixture.requests.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }
        assertEquals(DeliveryStatus.FAILED, shown().single().deliveryStatus)

        assertEquals(Outcome.Success(Unit), interactor.retry(request.idempotencyKey))
        repository.deliverOpen()

        assertEquals(2, scheduler.calls)
        val row = shown().single()
        assertEquals(request.idempotencyKey, row.request.idempotencyKey)
        assertEquals(DeliveryStatus.DELIVERED, row.deliveryStatus)
        assertEquals("req-1", row.request.requestId)
        assertEquals(5, entry(request.idempotencyKey)?.attempts)
    }

    @Test
    fun `track known as not broadcastable is refused without touching outbox or archive`() = runTest {
        fixture.archive.nextFailure = Failure.Connection

        val result = interactor.submitRequest(track("trk-3", broadcastable = false), message = null)

        assertEquals(Submission.Refused(RefusalReason.TRACK_NOT_BROADCASTABLE), result)
        assertTrue(outboxDao.openEntries().isEmpty())
        assertEquals(0, scheduler.calls)
        assertEquals(Failure.Connection, fixture.archive.nextFailure)
    }

    @Test
    fun `unknown flag is looked up in the archive`() = runTest {
        val result = interactor.submitRequest(track("trk-3"), message = null)

        assertEquals(Submission.Refused(RefusalReason.TRACK_NOT_BROADCASTABLE), result)
    }

    @Test
    fun `unknown flag with unreachable archive still queues and leaves the decision to the station`() = runTest {
        fixture.archive.nextFailure = Failure.Connection

        assertTrue(interactor.submitRequest(track("trk-3"), message = null) is Submission.Queued)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `unknown track id fails with the archive rejection`() = runTest {
        val result = interactor.submitRequest(track("trk-404"), message = null)

        assertTrue((result as Submission.Failed).failure is Failure.Rejected)
        assertTrue(outboxDao.openEntries().isEmpty())
    }

    @Test
    fun `station rejection arrives with its reason and is not sent again`() = runTest {
        submit()
        fixture.requests.nextFailure = Failure.Rejected(reason = "limit reached", retryable = true)
        repository.deliverOpen()
        fixture.requests.nextFailure = Failure.Server
        repository.deliverOpen()

        val row = shown().single()
        assertEquals(RequestStatus.REJECTED, row.request.status)
        assertEquals("limit reached", row.request.rejectionReason)
        assertEquals(DeliveryStatus.REJECTED, row.deliveryStatus)
        assertEquals(Failure.Server, fixture.requests.nextFailure)
    }

    @Test
    fun `technical failure is not a rejection`() = runTest {
        val request = submit()
        fixture.requests.nextFailure = Failure.Server
        repository.deliverOpen()

        val row = shown().single()
        assertEquals(RequestStatus.PENDING, row.request.status)
        assertNull(row.request.rejectionReason)
        assertEquals(DeliveryStatus.OPEN, row.deliveryStatus)
        assertEquals(1, entry(request.idempotencyKey)?.attempts)
    }

    @Test
    fun `blank query returns nothing without asking the archive`() = runTest {
        fixture.archive.nextFailure = Failure.Connection

        assertEquals(Outcome.Success(emptyList<Track>()), interactor.searchTracks("   "))
        assertEquals(Failure.Connection, fixture.archive.nextFailure)
    }

    @Test
    fun `search trims the query and passes archive hits through`() = runTest {
        val hits = (interactor.searchTracks(" song ") as Outcome.Success).value

        assertEquals(listOf("trk-1", "trk-2", "trk-3"), hits.map { it.trackId })
    }

    @Test
    fun `refreshStatuses takes over the station status for the listener`() = runTest {
        val request = submit()
        repository.deliverOpen()

        assertEquals(Outcome.Success(Unit), interactor.refreshStatuses())

        assertEquals("req-1", shown().single().request.requestId)
        assertEquals(request.idempotencyKey, shown().single().request.idempotencyKey)
    }
}
