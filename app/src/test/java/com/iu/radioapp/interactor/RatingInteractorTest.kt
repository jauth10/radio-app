package com.iu.radioapp.interactor

import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingContext
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.Submission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingInteractorTest {

    private val fixture = InteractorFixture()
    private val interactor = fixture.ratingInteractor
    private val repository = fixture.ratingRepository
    private val playout = fixture.playout
    private val scheduler = fixture.scheduler

    private suspend fun context(): RatingContext? = (interactor.getRatingContext() as Outcome.Success).value

    private suspend fun rate(target: RatingTarget = RatingTarget.PLAYLIST, value: Int = 4): Rating =
        (interactor.submitRating(target, value, comment = null) as Submission.Queued).value

    private suspend fun deliveries() = interactor.observeDeliveries().first()

    @Test
    fun `context carries show and host while a hosted track is live`() = runTest {
        val context = checkNotNull(context())

        assertEquals("show-1", context.show.showId)
        assertEquals("host-1", context.host?.hostId)
    }

    @Test
    fun `context from the cache knows the show but not the host`() = runTest {
        context()
        playout.nextFailure = Failure.Connection

        val context = checkNotNull(context())

        assertEquals("show-1", context.show.showId)
        assertNull(context.host)
    }

    @Test
    fun `talk segment gives no context`() = runTest {
        playout.currentTrack = null

        assertNull(context())
    }

    @Test
    fun `connection failure without a cache is passed on`() = runTest {
        playout.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), interactor.getRatingContext())
    }

    @Test
    fun `playlist rating refers to the show and host rating to the host`() = runTest {
        val playlist = rate(RatingTarget.PLAYLIST)
        val host = rate(RatingTarget.HOST, value = 5)

        assertEquals("show-1", playlist.referenceId)
        assertEquals("host-1", host.referenceId)
        assertNotEquals(playlist.idempotencyKey, host.idempotencyKey)
        assertEquals(2, scheduler.calls)
        assertEquals(listOf(DeliveryStatus.OPEN, DeliveryStatus.OPEN), deliveries().map { it.status })
    }

    @Test
    fun `rating during a talk segment is refused`() = runTest {
        playout.currentTrack = null

        val result = interactor.submitRating(RatingTarget.PLAYLIST, 3, comment = null)

        assertEquals(Submission.Refused(RefusalReason.NO_SHOW_ON_AIR), result)
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun `host rating without a known host is refused`() = runTest {
        playout.currentTrack = playout.currentTrack?.copy(hostId = null, hostName = null)

        val result = interactor.submitRating(RatingTarget.HOST, 3, comment = null)

        assertEquals(Submission.Refused(RefusalReason.HOST_UNKNOWN), result)
        assertTrue(deliveries().isEmpty())
    }

    @Test
    fun `host rating from the cache is refused, playlist rating still goes through`() = runTest {
        context()
        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = 2

        assertEquals(Submission.Refused(RefusalReason.HOST_UNKNOWN), interactor.submitRating(RatingTarget.HOST, 3, null))
        assertEquals("show-1", rate(RatingTarget.PLAYLIST).referenceId)
    }

    @Test
    fun `key stays stable through failed attempts and manual retry`() = runTest {
        val rating = rate()
        fixture.feedback.nextFailure = Failure.Connection
        fixture.feedback.failureRepeatCount = 5
        repeat(5) { repository.deliverOpen() }
        assertEquals(DeliveryStatus.FAILED, deliveries().single().status)

        assertEquals(Outcome.Success(Unit), interactor.retry(rating.idempotencyKey))
        repository.deliverOpen()

        assertEquals(2, scheduler.calls)
        val delivered = deliveries().single()
        assertEquals(rating.idempotencyKey, delivered.idempotencyKey)
        assertEquals(DeliveryStatus.DELIVERED, delivered.status)
    }

    @Test
    fun `second rating of the same target comes back rejected with the station reason`() = runTest {
        rate()
        repository.deliverOpen()
        val second = rate()
        repository.deliverOpen()

        val rejected = deliveries().single { it.idempotencyKey == second.idempotencyKey }
        assertEquals(DeliveryStatus.REJECTED, rejected.status)
        assertEquals("already rated", rejected.rejectionReason)
    }

    @Test
    fun `technical failure is not a rejection`() = runTest {
        rate()
        fixture.feedback.nextFailure = Failure.Server
        repository.deliverOpen()

        val entry = deliveries().single()
        assertEquals(DeliveryStatus.OPEN, entry.status)
        assertEquals(1, entry.attempts)
        assertNull(entry.rejectionReason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `value outside one to five is a programming error`() = runTest {
        interactor.submitRating(RatingTarget.PLAYLIST, 6, comment = null)
    }
}
