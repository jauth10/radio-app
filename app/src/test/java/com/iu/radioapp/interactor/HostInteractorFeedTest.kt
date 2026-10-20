package com.iu.radioapp.interactor

import com.iu.radioapp.repository.firstValue
import com.iu.radioapp.data.remote.s4feedback.ConnectionEvent
import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.domain.HostFeed
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.repository.MutableClock
import com.iu.radioapp.repository.TEST_NOW
import contract.s4feedback.AggregateDto
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HostInteractorFeedTest {

    private val scheduler = TestCoroutineScheduler()
    private val fixture = InteractorFixture(MutableClock(scheduler = scheduler))
    private val interactor = fixture.hostInteractor

    private fun feedTest(block: suspend TestScope.(List<HostFeed>) -> Unit) = runTest(StandardTestDispatcher(scheduler)) {
        val feeds = mutableListOf<HostFeed>()
        backgroundScope.launch { interactor.observeHostFeed().toList(feeds) }
        runCurrent()
        block(feeds)
    }

    private fun TestScope.emit(vararg sequence: ConnectionEvent) {
        sequence.forEach { fixture.ratingEvents.emit(it) }
        runCurrent()
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private suspend fun TestScope.login() {
        interactor.login("1234")
        runCurrent()
    }

    private fun pushedRating(ratingId: String, at: Instant) = ConnectionEvent.MessageReceived(
        RatingEventMessage(
            EventType.NEW_RATING,
            t0 = at,
            rating = RatingEventDto(ratingId, contract.s4feedback.RatingTarget.PLAYLIST, 5, "Super", at, null),
        ),
    )

    private val List<HostFeed>.active get() = last() as HostFeed.Active

    @Test
    fun `without a session the feed is signed out and nothing connects`() = feedTest { feeds ->
        assertEquals(listOf(HostFeed.SignedOut), feeds)
        assertNull(fixture.ratingEvents.lastRequestedToken)
    }

    @Test
    fun `after login the feed connects with the session token for the show on air`() = feedTest { feeds ->
        login()

        val listenerId = (fixture.listenerRepository.getListener() as Outcome.Success).value.listenerId
        assertEquals("fake-session-$listenerId", fixture.ratingEvents.lastRequestedToken)
        assertEquals(ConnectionState.Connecting, feeds.active.connection)
        assertEquals("show-1", feeds.active.showId)
        assertEquals(1, feeds.active.aggregate?.playlistRatingCount)
    }

    @Test
    fun `ratings and aggregate are merged, newest first`() = feedTest { feeds ->
        login()
        val aggregate = AggregateDto(5.0, 3, 0.0, 0, TimeWindow(TEST_NOW, TEST_NOW))

        emit(
            ConnectionEvent.Connected,
            pushedRating("rat-a", TEST_NOW),
            pushedRating("rat-b", TEST_NOW + 2.seconds),
            ConnectionEvent.MessageReceived(RatingEventMessage(EventType.AGGREGATE_UPDATED, t0 = TEST_NOW, aggregate = aggregate)),
        )

        assertEquals(listOf("rat-b", "rat-a"), feeds.active.ratings.map { it.rating.ratingId })
        assertEquals(3, feeds.active.aggregate?.playlistRatingCount)
        assertEquals(ConnectionState.Connected, feeds.active.connection)
    }

    @Test
    fun `a rejected session is ended without touching the listener side`() = feedTest { feeds ->
        val listenerId = (fixture.listenerRepository.getListener() as Outcome.Success).value.listenerId
        val queued = fixture.ratingInteractor.submitRating(RatingTarget.PLAYLIST, 4, null)
        login()

        emit(ConnectionEvent.Connected, ConnectionEvent.Failed(Failure.Unauthorized))

        assertEquals(HostFeed.SessionExpired, feeds.last())
        assertNull(fixture.preferences.hostSessionToken.first())
        assertEquals(listenerId, (fixture.listenerRepository.getListener() as Outcome.Success).value.listenerId)
        assertEquals(
            listOf((queued as Submission.Queued).value.idempotencyKey),
            fixture.ratingInteractor.observeDeliveries().firstValue().map { it.idempotencyKey },
        )
    }

    @Test
    fun `the session ends when its validity runs out`() = feedTest { feeds ->
        login()

        advance(59.minutes + 59.seconds)
        assertEquals(HostFeed.Active::class, feeds.last()::class)

        advance(1.seconds)

        assertEquals(HostFeed.SessionExpired, feeds.last())
        assertNull(fixture.preferences.hostSessionToken.first())
    }

    @Test
    fun `logging in again after expiry starts a new feed, logging out is signed out`() = feedTest { feeds ->
        login()
        emit(ConnectionEvent.Failed(Failure.Unauthorized))

        login()
        assertEquals(HostFeed.Active::class, feeds.last()::class)

        interactor.endSession()
        runCurrent()

        assertEquals(HostFeed.SignedOut, feeds.last())
    }

    @Test
    fun `a show from a stale cache is not used`() = feedTest { feeds ->
        fixture.trackRepository.getCurrentPlayback()
        fixture.clock.instant = TEST_NOW + 5.minutes + 1.seconds
        interactor.login("1234")
        fixture.playout.nextFailure = Failure.Connection
        runCurrent()

        assertNull(feeds.active.showId)
        assertNull(feeds.active.aggregate)
    }

    @Test
    fun `ratings since login are loaded when the feed starts, older ones are not`() = runTest(StandardTestDispatcher(scheduler)) {
        fixture.ratingInteractor.submitRating(RatingTarget.PLAYLIST, 3, "Vor der Anmeldung")
        fixture.ratingRepository.deliverOpen()
        advanceTimeBy(10.minutes)
        login()
        fixture.ratingInteractor.submitRating(RatingTarget.HOST, 5, "Nach der Anmeldung")
        fixture.ratingRepository.deliverOpen()

        val feeds = mutableListOf<HostFeed>()
        backgroundScope.launch { interactor.observeHostFeed().toList(feeds) }
        runCurrent()

        val received = feeds.active.ratings.single()
        assertEquals("Nach der Anmeldung", received.rating.comment)
        assertEquals(FeedSource.CATCH_UP, received.source)
    }
}
