package com.iu.radioapp.repository

import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.data.local.toDomain
import com.iu.radioapp.data.remote.s4feedback.ConnectionEvent
import com.iu.radioapp.data.remote.s4feedback.FeedbackDataSource
import com.iu.radioapp.data.remote.s4feedback.RatingEventDataSource
import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.OutboxEntry
import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.RatingEvent
import com.iu.radioapp.domain.RatingFeedSignal
import com.iu.radioapp.domain.RatingWithDelivery
import com.iu.radioapp.domain.ReceivedRating
import com.iu.radioapp.domain.map
import com.iu.radioapp.repository.mapping.toDomain
import com.iu.radioapp.repository.mapping.toRequestDto
import contract.common.RadioJson
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.RatingRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RatingRepository @Inject constructor(
    private val feedback: FeedbackDataSource,
    private val events: RatingEventDataSource,
    private val outboxDao: OutboxDao,
    private val clock: Clock,
) {

    suspend fun enqueue(rating: Rating): Outcome<Unit> = localOutcome {
        outboxDao.insert(
            OutboxEntity(
                idempotencyKey = rating.idempotencyKey,
                operation = OperationType.RATING,
                payload = RadioJson.encodeToString(rating.toRequestDto()),
                attempts = 0,
                lastAttemptAt = null,
                status = DeliveryStatus.OPEN,
                rejectionReason = null,
            )
        )
    }

    suspend fun deliverOpen(): Outcome<Unit> = outboxDao.deliverOpenEntries(
        operation = OperationType.RATING,
        clock = clock,
        send = { entry ->
            feedback.submitRating(entry.idempotencyKey, RadioJson.decodeFromString<RatingRequest>(entry.payload))
        },
        onDelivered = { entry, _ -> outboxDao.markDelivered(entry.id) },
        onRejected = { entry, reason -> outboxDao.markRejected(entry.id, reason) },
    )

    suspend fun retry(idempotencyKey: String): Outcome<Unit> = localOutcome {
        outboxDao.reopenIfFailed(idempotencyKey)
    }

    suspend fun getAggregate(showId: String): Outcome<RatingAggregate> =
        feedback.getAggregate(showId).map { it.toDomain() }

    suspend fun getRatingsSince(since: Instant, showId: String, token: String): Outcome<List<RatingEvent>> =
        feedback.getRatingsSince(since, showId, token).map { events -> events.map { it.toDomain() } }

    fun observeDeliveries(): Flow<Outcome<List<OutboxEntry>>> =
        outboxDao.observeAll().map { entries ->
            entries.filter { it.operation == OperationType.RATING }.map { it.toDomain() }
        }.asLocalOutcomes()

    /**
     * Failed and rejected ratings, newest first; read back from the stored payload, since ratings have no table of
     * their own. An unreadable payload is skipped: it could never be resent, and it must not block the readable ones.
     */
    fun observeUndeliveredRatings(): Flow<Outcome<List<RatingWithDelivery>>> =
        outboxDao.observeAll().map { entries ->
            entries.filter { it.operation == OperationType.RATING && it.status in UNDELIVERED }
                .sortedByDescending { it.id }
                .mapNotNull { entry ->
                    entry.toRatingOrNull()?.let { RatingWithDelivery(it, entry.status, entry.rejectionReason) }
                }
        }.asLocalOutcomes()

    suspend fun dismissRejected(idempotencyKey: String): Outcome<Unit> = localOutcome {
        val entry = outboxDao.findByIdempotencyKey(idempotencyKey)
        if (entry?.status == DeliveryStatus.REJECTED) outboxDao.dismissRejectedRating(entry.id)
    }

    private fun OutboxEntity.toRatingOrNull(): Rating? =
        try {
            RadioJson.decodeFromString<RatingRequest>(payload).toDomain(idempotencyKey)
        } catch (e: SerializationException) {
            null
        }

    /**
     * Event channel and polling fallback as one stream, starting with the ratings received since [since].
     * Which source is used is decided here, not in the event client; without a [showId] nothing is loaded or polled.
     */
    fun observeRatingFeed(token: String, showId: String?, since: Instant): Flow<RatingFeedSignal> = channelFlow {
        val rejected = CompletableDeferred<Unit>()
        launch { FeedRun(this@channelFlow, token, showId, since, rejected).run() }
        rejected.await()
        coroutineContext.cancelChildren()
        send(RatingFeedSignal.SessionRejected)
    }

    private enum class ChannelEnd { REJECTED, LOST }

    private inner class FeedRun(
        private val out: ProducerScope<RatingFeedSignal>,
        private val token: String,
        private val showId: String?,
        since: Instant,
        private val rejected: CompletableDeferred<Unit>,
    ) {
        private val lock = Mutex()
        private val seen = HashSet<String>()

        // The margin covers a device clock running ahead of the station's.
        private var cursor: Instant = since - INITIAL_LOOKBACK
        private var connected = false
        private var polling: Job? = null

        suspend fun run() {
            out.send(RatingFeedSignal.StateChanged(ConnectionState.Connecting))
            out.launch { catchUp() }
            while (true) {
                if (follow() == ChannelEnd.REJECTED) {
                    rejected.complete(Unit)
                    return
                }
                lock.withLock {
                    connected = false
                    if (polling == null) {
                        out.send(RatingFeedSignal.StateChanged(ConnectionState.Polling))
                        polling = out.launch { poll() }
                    }
                }
                delay(PROBE_INTERVAL)
            }
        }

        // While polling, a reconnect is a probe: its attempts stay invisible until it is confirmed.
        private suspend fun follow(): ChannelEnd = coroutineScope {
            var confirmation: Job? = null
            val end = events.events(token).firstOrNull { event ->
                when (event) {
                    ConnectionEvent.Connected -> {
                        confirmation = launch {
                            delay(CONFIRMATION_DELAY)
                            confirm()
                        }
                        false
                    }
                    is ConnectionEvent.MessageReceived -> {
                        confirmation?.cancel()
                        confirm()
                        handle(event.message)
                        false
                    }
                    is ConnectionEvent.Reconnecting -> {
                        confirmation?.cancel()
                        lock.withLock {
                            connected = false
                            if (polling == null) {
                                out.send(RatingFeedSignal.StateChanged(ConnectionState.Reconnecting(event.failedAttempts)))
                            }
                        }
                        false
                    }
                    is ConnectionEvent.Failed -> true
                }
            }
            confirmation?.cancel()
            if ((end as? ConnectionEvent.Failed)?.failure == Failure.Unauthorized) ChannelEnd.REJECTED else ChannelEnd.LOST
        }

        private suspend fun confirm(): Unit = lock.withLock {
            if (connected) return@withLock
            connected = true
            polling?.cancel()
            polling = null
            out.send(RatingFeedSignal.StateChanged(ConnectionState.Connected))
        }

        private suspend fun handle(message: RatingEventMessage) {
            when (message.type) {
                EventType.NEW_RATING -> message.rating?.let { deliver(it.toDomain(), FeedSource.EVENT_CHANNEL) }
                EventType.AGGREGATE_UPDATED -> message.aggregate?.let {
                    out.send(RatingFeedSignal.AggregateUpdated(it.toDomain()))
                }
            }
        }

        // A failed catch-up only leaves the list shorter; a rejected token ends the session as anywhere else.
        private suspend fun catchUp() {
            val showId = showId ?: return
            when (val outcome = getRatingsSince(lock.withLock { cursor }, showId, token)) {
                is Outcome.Success -> outcome.value.sortedBy { it.serverReceivedAt }
                    .forEach { deliver(it, FeedSource.CATCH_UP) }
                is Outcome.Error -> if (outcome.failure == Failure.Unauthorized) rejected.complete(Unit)
            }
        }

        private suspend fun poll() {
            val showId = showId ?: return
            while (true) {
                // The overlap catches ratings the event channel missed shortly before it dropped.
                val since = lock.withLock { cursor } - POLL_OVERLAP
                when (val outcome = getRatingsSince(since, showId, token)) {
                    is Outcome.Success -> outcome.value.sortedBy { it.serverReceivedAt }
                        .forEach { deliver(it, FeedSource.POLLING) }
                    is Outcome.Error -> if (outcome.failure == Failure.Unauthorized) {
                        rejected.complete(Unit)
                        return
                    }
                }
                when (val outcome = getAggregate(showId)) {
                    is Outcome.Success -> out.send(RatingFeedSignal.AggregateUpdated(outcome.value))
                    is Outcome.Error -> if (outcome.failure == Failure.Unauthorized) {
                        rejected.complete(Unit)
                        return
                    }
                }
                delay(POLL_INTERVAL)
            }
        }

        private suspend fun deliver(rating: RatingEvent, source: FeedSource): Unit = lock.withLock {
            if (rating.serverReceivedAt > cursor) cursor = rating.serverReceivedAt
            if (seen.add(rating.ratingId)) {
                out.send(RatingFeedSignal.RatingReceived(ReceivedRating(rating, source)))
            }
        }
    }

    companion object {
        val POLL_INTERVAL = 10.seconds
        val PROBE_INTERVAL = 60.seconds
        val CONFIRMATION_DELAY = 1.seconds
        val POLL_OVERLAP = 30.seconds
        val INITIAL_LOOKBACK = 1.minutes
        private val UNDELIVERED = setOf(DeliveryStatus.FAILED, DeliveryStatus.REJECTED)
    }
}
