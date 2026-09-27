package com.iu.radioapp.repository

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.FakeSongRequestDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.local.toEntity
import com.iu.radioapp.data.remote.s3requests.FakeRequestsDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RequestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class SongRequestRepositoryTest {

    private val requests = FakeRequestsDataSource()
    private val store = InMemoryLocalStore()
    private val outboxDao = FakeOutboxDao(store)
    private val songRequestDao = FakeSongRequestDao(store)
    private val clock = MutableClock()
    private val repository = SongRequestRepository(requests, outboxDao, songRequestDao, clock)

    private suspend fun entry(key: String = "key-1") = outboxDao.findByIdempotencyKey(key)
    private suspend fun row(key: String = "key-1") = songRequestDao.findRequest(key)

    @Test
    fun `enqueue creates the request row and an open entry right away`() = runTest {
        repository.enqueue(songRequest(), displayName = "Jo")

        assertEquals(DeliveryStatus.OPEN, entry()?.status)
        assertEquals(0, entry()?.attempts)
        assertEquals(RequestStatus.PENDING, row()?.status)
        assertNull(row()?.requestId)
        assertEquals(listOf("key-1"), repository.observeRequests("listener-1").first().map { it.idempotencyKey })
    }

    @Test
    fun `delivery books the station response`() = runTest {
        repository.enqueue(songRequest(), displayName = null)

        assertEquals(Outcome.Success(Unit), repository.deliverOpen())

        assertEquals(DeliveryStatus.DELIVERED, entry()?.status)
        assertEquals("req-1", row()?.requestId)
    }

    @Test
    fun `rejection sets entry and request to rejected with the reason`() = runTest {
        repository.enqueue(songRequest(trackId = "trk-3"), displayName = null)

        assertEquals(Outcome.Success(Unit), repository.deliverOpen())

        assertEquals(DeliveryStatus.REJECTED, entry()?.status)
        assertEquals("track not broadcastable: trk-3", entry()?.rejectionReason)
        assertEquals(RequestStatus.REJECTED, row()?.status)
        assertEquals("track not broadcastable: trk-3", row()?.rejectionReason)
    }

    @Test
    fun `rejected request is not sent again even when the station says retryable`() = runTest {
        repository.enqueue(songRequest(), displayName = null)
        requests.nextFailure = Failure.Rejected(reason = "limit reached", retryable = true)
        repository.deliverOpen()

        requests.nextFailure = Failure.Server
        repository.deliverOpen()

        assertEquals(Failure.Server, requests.nextFailure)
        assertEquals(DeliveryStatus.REJECTED, entry()?.status)
        assertEquals(0, entry()?.attempts)
    }

    @Test
    fun `connection failure counts an attempt and keeps the entry open`() = runTest {
        repository.enqueue(songRequest(), displayName = null)
        requests.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), repository.deliverOpen())

        assertEquals(DeliveryStatus.OPEN, entry()?.status)
        assertEquals(1, entry()?.attempts)
        assertEquals(TEST_NOW, entry()?.lastAttemptAt)
        assertNull(entry()?.rejectionReason)
    }

    @Test
    fun `connection failure stops the run before the next entry`() = runTest {
        repository.enqueue(songRequest("key-1"), displayName = null)
        repository.enqueue(songRequest("key-2"), displayName = null)
        requests.nextFailure = Failure.Connection

        repository.deliverOpen()

        assertEquals(0, entry("key-2")?.attempts)
    }

    @Test
    fun `fifth server failure marks the entry failed without a reason`() = runTest {
        repository.enqueue(songRequest(), displayName = null)
        requests.nextFailure = Failure.Server
        requests.failureRepeatCount = 5

        repeat(5) { repository.deliverOpen() }

        assertEquals(DeliveryStatus.FAILED, entry()?.status)
        assertEquals(5, entry()?.attempts)
        assertNull(entry()?.rejectionReason)
        assertEquals(RequestStatus.PENDING, row()?.status)
    }

    @Test
    fun `retry reopens a failed entry and delivers under the same idempotency key`() = runTest {
        repository.enqueue(songRequest(), displayName = null)
        requests.nextFailure = Failure.Server
        requests.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }

        repository.retry("key-1")
        repository.deliverOpen()

        assertEquals(DeliveryStatus.DELIVERED, entry()?.status)
        assertEquals(5, entry()?.attempts)
        assertEquals("req-1", row()?.requestId)
    }

    @Test
    fun `reopened entry gets five more attempts`() = runTest {
        repository.enqueue(songRequest(), displayName = null)
        requests.nextFailure = Failure.Server
        requests.failureRepeatCount = 10
        repeat(5) { repository.deliverOpen() }
        repository.retry("key-1")

        repeat(4) { repository.deliverOpen() }
        assertEquals(DeliveryStatus.OPEN, entry()?.status)
        repository.deliverOpen()

        assertEquals(DeliveryStatus.FAILED, entry()?.status)
        assertEquals(10, entry()?.attempts)
    }

    @Test
    fun `retry leaves a rejected entry alone`() = runTest {
        repository.enqueue(songRequest(trackId = "trk-3"), displayName = null)
        repository.deliverOpen()

        repository.retry("key-1")

        assertEquals(DeliveryStatus.REJECTED, entry()?.status)
    }

    @Test
    fun `refreshStatuses takes over status reason and schedule from the station`() = runTest {
        songRequestDao.insert(songRequest("key-a").copy(requestId = "req-seed-accepted").toEntity())
        songRequestDao.insert(songRequest("key-r").copy(requestId = "req-seed-rejected").toEntity())

        assertEquals(Outcome.Success(Unit), repository.refreshStatuses("listener-1"))

        assertEquals(RequestStatus.ACCEPTED, row("key-a")?.status)
        assertEquals(Instant.parse("2026-08-29T18:00:00Z"), row("key-a")?.scheduledBroadcast)
        assertEquals(RequestStatus.REJECTED, row("key-r")?.status)
        assertEquals("Titel nicht im Bestand", row("key-r")?.rejectionReason)
    }

    @Test
    fun `refreshStatuses passes a server failure on and keeps the rows`() = runTest {
        songRequestDao.insert(songRequest("key-a").copy(requestId = "req-seed-accepted").toEntity())
        requests.nextFailure = Failure.Server

        assertEquals(Outcome.Error(Failure.Server), repository.refreshStatuses("listener-1"))

        assertEquals(RequestStatus.PENDING, row("key-a")?.status)
    }
}
