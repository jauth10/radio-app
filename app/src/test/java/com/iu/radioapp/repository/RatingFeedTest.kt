package com.iu.radioapp.repository

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.remote.s4feedback.ConnectionEvent
import com.iu.radioapp.data.remote.s4feedback.FakeFeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeRatingEventDataSource
import com.iu.radioapp.data.remote.s4feedback.RatingEventDataSource
import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingFeedSignal
import contract.s4feedback.AggregateDto
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingTarget
import contract.s4feedback.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RatingFeedTest {

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock(scheduler = scheduler)
    private val feedback = FakeFeedbackDataSource(clock)
    private val events = FakeRatingEventDataSource()
    private val repository = repositoryWith(events)
    private var listenerCount = 0

    private fun repositoryWith(source: RatingEventDataSource) =
        RatingRepository(feedback, source, FakeOutboxDao(InMemoryLocalStore()), clock)

    private fun feedTest(
        showId: String? = SHOW_ID,
        repository: RatingRepository = this.repository,
        block: suspend TestScope.(List<RatingFeedSignal>) -> Unit,
    ) = runTest(StandardTestDispatcher(scheduler)) {
        val signals = mutableListOf<RatingFeedSignal>()
        backgroundScope.launch { repository.observeRatingFeed(TOKEN, showId).toList(signals) }
        runCurrent()
        block(signals)
    }

    private fun TestScope.emit(vararg sequence: ConnectionEvent) {
        sequence.forEach { events.emit(it) }
        runCurrent()
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun TestScope.connect() {
        emit(ConnectionEvent.Connected)
        advance(1.seconds)
    }

    private fun TestScope.dropToPolling() =
        emit(*(1..4).map { ConnectionEvent.Reconnecting(it) }.toTypedArray(), ConnectionEvent.Failed(Failure.Connection))

    // A rating the station has received, whether or not the event channel tells about it.
    private suspend fun receivedByStation(): RatingEventDto {
        val listenerId = "listener-${++listenerCount}"
        val request = RatingRequest(RatingTarget.PLAYLIST, SHOW_ID, 4, null, listenerId, clock.now())
        val ratingId = (feedback.submitRating(listenerId, request) as Outcome.Success).value.ratingId
        return RatingEventDto(ratingId, RatingTarget.PLAYLIST, 4, null, clock.now(), null)
    }

    private fun pushed(rating: RatingEventDto) =
        ConnectionEvent.MessageReceived(RatingEventMessage(EventType.NEW_RATING, t0 = rating.serverReceivedAt, rating = rating))

    private fun List<RatingFeedSignal>.states() =
        filterIsInstance<RatingFeedSignal.StateChanged>().map { it.state }

    private fun List<RatingFeedSignal>.received() =
        filterIsInstance<RatingFeedSignal.RatingReceived>().map { it.received }

    private fun List<RatingFeedSignal>.ratingIds() = received().map { it.rating.ratingId }

    @Test
    fun `connected is shown only after a second without failure`() = feedTest { signals ->
        emit(ConnectionEvent.Connected)
        advance(999.milliseconds)
        assertEquals(listOf(ConnectionState.Connecting), signals.states())

        advance(1.milliseconds)

        assertEquals(listOf(ConnectionState.Connecting, ConnectionState.Connected), signals.states())
    }

    @Test
    fun `a first message confirms the connection at once`() = feedTest { signals ->
        val rating = receivedByStation()

        emit(ConnectionEvent.Connected, pushed(rating))

        assertEquals(listOf(ConnectionState.Connecting, ConnectionState.Connected), signals.states())
        assertEquals(listOf(rating.ratingId), signals.ratingIds())
    }

    @Test
    fun `a rejected token right after the handshake ends the session without polling`() = feedTest { signals ->
        feedback.nextFailure = Failure.Server

        emit(ConnectionEvent.Connected, ConnectionEvent.Failed(Failure.Unauthorized))
        advance(2.minutes)

        assertEquals(
            listOf(RatingFeedSignal.StateChanged(ConnectionState.Connecting), RatingFeedSignal.SessionRejected),
            signals,
        )
        assertEquals(Failure.Server, feedback.nextFailure)
    }

    @Test
    fun `the connection state goes through every transition`() = feedTest { signals ->
        emit(ConnectionEvent.Reconnecting(1))
        connect()
        dropToPolling()
        advance(60.seconds)
        connect()

        assertEquals(
            listOf(
                ConnectionState.Connecting,
                ConnectionState.Reconnecting(1),
                ConnectionState.Connected,
                ConnectionState.Reconnecting(1),
                ConnectionState.Reconnecting(2),
                ConnectionState.Reconnecting(3),
                ConnectionState.Reconnecting(4),
                ConnectionState.Polling,
                ConnectionState.Connected,
            ),
            signals.states(),
        )
    }

    @Test
    fun `after five failed attempts ratings are polled every ten seconds`() = feedTest { signals ->
        connect()
        dropToPolling()
        advance(1.seconds)
        val rating = receivedByStation()

        advance(8.seconds + 999.milliseconds)
        assertEquals(emptyList<String>(), signals.ratingIds())

        advance(1.milliseconds)

        assertEquals(listOf(rating.ratingId), signals.ratingIds())
        assertEquals(FeedSource.POLLING, signals.received().single().source)
    }

    @Test
    fun `an event stream that ends without failure also switches to polling`() {
        val endsAfterHandshake = object : RatingEventDataSource {
            override fun events(token: String): Flow<ConnectionEvent> = flowOf(ConnectionEvent.Connected)
        }
        feedTest(repository = repositoryWith(endsAfterHandshake)) { signals ->
            assertEquals(listOf(ConnectionState.Connecting, ConnectionState.Polling), signals.states())
        }
    }

    @Test
    fun `polling carries on after connection and server failures`() = feedTest { signals ->
        connect()
        feedback.nextFailure = Failure.Connection
        dropToPolling()
        val rating = receivedByStation()
        feedback.nextFailure = Failure.Server

        advance(10.seconds)
        advance(10.seconds)

        assertEquals(listOf(rating.ratingId), signals.ratingIds())
        assertEquals(ConnectionState.Polling, signals.states().last())
    }

    @Test
    fun `unauthorized while polling ends the session`() = feedTest { signals ->
        connect()
        feedback.nextFailure = Failure.Unauthorized
        dropToPolling()
        val count = signals.size

        advance(1.minutes)

        assertEquals(RatingFeedSignal.SessionRejected, signals.last())
        assertEquals(count, signals.size)
    }

    @Test
    fun `a rating arriving over both paths is shown once and keeps its server time`() = feedTest { signals ->
        connect()
        val rating = receivedByStation()
        emit(pushed(rating))

        dropToPolling()
        advance(10.seconds)

        val received = signals.received().single()
        assertEquals(rating.ratingId, received.rating.ratingId)
        assertEquals(FeedSource.EVENT_CHANNEL, received.source)
        assertEquals(rating.serverReceivedAt, received.rating.serverReceivedAt)
    }

    @Test
    fun `a rating missed while reconnecting is picked up by the first poll`() = feedTest { signals ->
        connect()
        emit(ConnectionEvent.Reconnecting(1))
        advance(2.seconds)
        val missed = receivedByStation()

        emit(ConnectionEvent.Reconnecting(2), ConnectionEvent.Reconnecting(3), ConnectionEvent.Reconnecting(4))
        emit(ConnectionEvent.Failed(Failure.Connection))

        assertEquals(listOf(missed.ratingId), signals.ratingIds())
    }

    @Test
    fun `the overlap catches a rating older than the newest one seen`() = feedTest { signals ->
        connect()
        val missed = receivedByStation()
        advance(20.seconds)
        val seen = receivedByStation()
        emit(pushed(seen))

        dropToPolling()

        assertEquals(setOf(seen.ratingId, missed.ratingId), signals.ratingIds().toSet())
        assertEquals(2, signals.ratingIds().size)
    }

    @Test
    fun `aggregates come from the event channel and from polling`() = feedTest { signals ->
        val pushedAggregate = AggregateDto(4.5, 2, 3.0, 1, TimeWindow(Instant.parse("2026-09-20T11:00:00Z"), TEST_NOW))
        connect()
        emit(
            ConnectionEvent.MessageReceived(
                RatingEventMessage(EventType.AGGREGATE_UPDATED, t0 = TEST_NOW, aggregate = pushedAggregate),
            ),
        )
        dropToPolling()

        val aggregates = signals.filterIsInstance<RatingFeedSignal.AggregateUpdated>().map { it.aggregate }
        assertEquals(listOf(2, 1), aggregates.map { it.playlistRatingCount })
    }

    @Test
    fun `without a known show nothing is polled`() = feedTest(showId = null) { signals ->
        feedback.nextFailure = Failure.Server
        dropToPolling()

        advance(1.minutes)

        assertEquals(ConnectionState.Polling, signals.states().last())
        assertEquals(Failure.Server, feedback.nextFailure)
    }

    @Test
    fun `a probe a minute later returns to the event channel and stops polling`() = feedTest { signals ->
        connect()
        dropToPolling()
        advance(59.seconds)
        emit(ConnectionEvent.Connected)
        advance(1.seconds)
        assertEquals(ConnectionState.Polling, signals.states().last())

        connect()
        receivedByStation()
        advance(30.seconds)

        assertEquals(ConnectionState.Connected, signals.states().last())
        assertEquals(emptyList<String>(), signals.ratingIds())
    }

    @Test
    fun `a failing probe stays invisible and polling goes on`() = feedTest { signals ->
        connect()
        dropToPolling()
        val statesBefore = signals.states()
        advance(60.seconds)

        dropToPolling()
        val rating = receivedByStation()
        advance(10.seconds)

        assertEquals(statesBefore, signals.states())
        assertEquals(listOf(rating.ratingId), signals.ratingIds())
    }

    private companion object {
        const val TOKEN = "session-1"
        const val SHOW_ID = FakeFeedbackDataSource.SEEDED_SHOW_ID
    }
}
