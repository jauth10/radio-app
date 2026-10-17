package com.iu.radioapp.repository

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.FakePlaybackHistoryDao
import com.iu.radioapp.data.local.FakeSongRequestDao
import com.iu.radioapp.data.local.FakeTrackCacheDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.local.LocalFault
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.data.local.inMemoryUserPreferences
import com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource
import com.iu.radioapp.data.remote.s3requests.FakeRequestsDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeFeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeRatingEventDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFailureTest {

    private val clock = MutableClock()
    private val fault = LocalFault()
    private val store = InMemoryLocalStore()
    private val outboxDao = FakeOutboxDao(store, fault)
    private val playout = FakePlayoutDataSource(clock)
    private val preferences = inMemoryUserPreferences(fault)

    private val tracks = TrackRepository(playout, FakeTrackCacheDao(fault), FakePlaybackHistoryDao(fault), clock)
    private val songRequests =
        SongRequestRepository(FakeRequestsDataSource(), outboxDao, FakeSongRequestDao(store, fault), clock)
    private val ratings = RatingRepository(FakeFeedbackDataSource(clock), FakeRatingEventDataSource(), outboxDao, clock)
    private val listeners = ListenerRepository(preferences)
    private val hosts = HostRepository(playout, preferences, clock)

    private val local = Outcome.Error(Failure.Local)

    private fun outboxEntity(key: String) = OutboxEntity(
        id = 1,
        idempotencyKey = key,
        operation = OperationType.RATING,
        payload = "{}",
        attempts = 0,
        lastAttemptAt = null,
        status = DeliveryStatus.OPEN,
        rejectionReason = null,
    )

    @Test
    fun `failing room flows end in a local error instead of throwing`() = runTest {
        fault.broken = true

        assertEquals(listOf(local), tracks.observeHistory().toList())
        assertEquals(listOf(local), songRequests.observeRequests("listener-1").toList())
        assertEquals(listOf(local), songRequests.observeDeliveries().toList())
        assertEquals(listOf(local), ratings.observeDeliveries().toList())
        assertEquals(listOf(local), ratings.observeFailedRatings().toList())
    }

    @Test
    fun `a failing datastore ends the session flow in a local error`() = runTest {
        fault.broken = true

        assertEquals(listOf(local), hosts.observeSession().toList())
    }

    @Test
    fun `a room flow that breaks while collected ends with a local error`() = runTest {
        val seen = mutableListOf<Outcome<*>>()
        val collecting = launch { ratings.observeDeliveries().toList(seen) }
        runCurrent()

        fault.broken = true
        store.outbox.value = listOf(outboxEntity("key-1"))
        collecting.join()

        assertEquals(listOf(Outcome.Success(emptyList<Nothing>()), local), seen)
    }

    @Test
    fun `a failing cache write turns a live track into a local error`() = runTest {
        fault.broken = true

        assertEquals(local, tracks.getCurrentPlayback())
        assertEquals(local, tracks.refreshHistory(limit = 10))
    }

    @Test
    fun `a failing cache read on the fallback is a local error, not the connection failure`() = runTest {
        tracks.getCurrentPlayback()
        playout.nextFailure = Failure.Connection
        fault.broken = true

        assertEquals(local, tracks.getCurrentPlayback())
    }

    @Test
    fun `failing writes from the screens come back as local errors`() = runTest {
        fault.broken = true

        assertEquals(local, songRequests.enqueue(songRequest(), displayName = null))
        assertEquals(local, songRequests.retry("key-1"))
        assertEquals(local, songRequests.refreshStatuses("listener-1"))
        assertEquals(local, ratings.enqueue(rating()))
        assertEquals(local, ratings.retry("key-1"))
        assertEquals(local, listeners.getListener())
        assertEquals(local, listeners.setDisplayName("Kim"))
        assertEquals(local, hosts.login("1234", "device-1"))
        assertEquals(local, hosts.endSession())
    }

    @Test
    fun `the storage works again once the fault is gone`() = runTest {
        fault.broken = true
        assertEquals(local, listeners.getListener())

        fault.broken = false

        assertTrue(listeners.getListener() is Outcome.Success)
        assertTrue(ratings.enqueue(rating()) is Outcome.Success)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not turned into a local error`() = runTest {
        localOutcome { throw CancellationException("cancelled") }
    }

    @Test(expected = CancellationException::class)
    fun `cancellation inside a flow is not turned into a local error`() = runTest {
        flow<Int> { throw CancellationException("cancelled") }.asLocalOutcomes().toList()
    }
}
