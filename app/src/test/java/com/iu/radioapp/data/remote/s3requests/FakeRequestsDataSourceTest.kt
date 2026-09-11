package com.iu.radioapp.data.remote.s3requests

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.RequestStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FakeRequestsDataSourceTest {

    private val fake = FakeRequestsDataSource()

    private fun sampleRequest(listenerId: String = "listener-1", trackId: String = "trk-1") = CreateSongRequestDto(
        trackId = trackId,
        listenerId = listenerId,
        displayName = "Test Listener",
        message = null,
        timestamp = Instant.parse("2026-08-28T10:00:00Z"),
    )

    @Test
    fun `submitRequest succeeds and is visible in status and overview`() = runTest {
        val outcome = fake.submitRequest("key-1", sampleRequest())

        val response = (outcome as Outcome.Success).value
        assertEquals(RequestStatus.PENDING, response.status)

        val status = fake.getRequestStatus(response.requestId) as Outcome.Success
        assertEquals(RequestStatus.PENDING, status.value.status)

        val overview = fake.getRequestsForListener("listener-1") as Outcome.Success
        assertEquals(1, overview.value.size)
        assertEquals(response.requestId, overview.value.first().requestId)
    }

    @Test
    fun `submitRequest with the same idempotency key does not create a second entry`() = runTest {
        val first = (fake.submitRequest("key-1", sampleRequest()) as Outcome.Success).value
        val second = (fake.submitRequest("key-1", sampleRequest()) as Outcome.Success).value

        assertEquals(first.requestId, second.requestId)
        val overview = (fake.getRequestsForListener("listener-1") as Outcome.Success).value
        assertEquals(1, overview.size)
    }

    @Test
    fun `submitRequest rejects a non-broadcastable track`() = runTest {
        val outcome = fake.submitRequest("key-x", sampleRequest(trackId = "trk-3"))
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `submitRequest rejects an unknown track`() = runTest {
        val outcome = fake.submitRequest("key-y", sampleRequest(trackId = "trk-does-not-exist"))
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `pre-seeded requests are visible in decided states`() = runTest {
        val accepted = (fake.getRequestStatus("req-seed-accepted") as Outcome.Success).value
        assertEquals(RequestStatus.ACCEPTED, accepted.status)

        val rejected = (fake.getRequestStatus("req-seed-rejected") as Outcome.Success).value
        assertEquals(RequestStatus.REJECTED, rejected.status)
        assertEquals("Titel nicht im Bestand", rejected.reason)

        val overview = (fake.getRequestsForListener("listener-seed") as Outcome.Success).value
        assertEquals(2, overview.size)
    }

    @Test
    fun `submitRequest returns Connection failure when set`() = runTest {
        fake.nextFailure = Failure.Connection
        val outcome = fake.submitRequest("key-2", sampleRequest())
        assertEquals(Failure.Connection, (outcome as Outcome.Error).failure)
    }

    @Test
    fun `submitRequest returns Server failure when set`() = runTest {
        fake.nextFailure = Failure.Server
        val outcome = fake.submitRequest("key-3", sampleRequest())
        assertEquals(Failure.Server, (outcome as Outcome.Error).failure)
    }

    @Test
    fun `submitRequest returns Rejected failure with reason and retryable when set`() = runTest {
        fake.nextFailure = Failure.Rejected(reason = "track not broadcastable", retryable = false)
        val outcome = fake.submitRequest("key-4", sampleRequest())
        val failure = (outcome as Outcome.Error).failure as Failure.Rejected
        assertEquals("track not broadcastable", failure.reason)
        assertFalse(failure.retryable)
    }

    @Test
    fun `submitRequest returns Unauthorized failure when set`() = runTest {
        fake.nextFailure = Failure.Unauthorized
        val outcome = fake.submitRequest("key-5", sampleRequest())
        assertEquals(Failure.Unauthorized, (outcome as Outcome.Error).failure)
    }

    @Test
    fun `a failure is one-shot by default and does not affect the next call`() = runTest {
        fake.nextFailure = Failure.Server
        fake.submitRequest("key-6", sampleRequest())

        val outcome = fake.submitRequest("key-7", sampleRequest())
        assertTrue(outcome is Outcome.Success)
    }

    @Test
    fun `failureRepeatCount fails that many calls in a row, then succeeds`() = runTest {
        fake.nextFailure = Failure.Server
        fake.failureRepeatCount = 2

        assertTrue(fake.submitRequest("key-8", sampleRequest()) is Outcome.Error)
        assertTrue(fake.submitRequest("key-9", sampleRequest()) is Outcome.Error)
        assertTrue(fake.submitRequest("key-10", sampleRequest()) is Outcome.Success)
    }

    @Test
    fun `getRequestStatus returns Rejected for an unknown requestId`() = runTest {
        val outcome = fake.getRequestStatus("does-not-exist")
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }
}
