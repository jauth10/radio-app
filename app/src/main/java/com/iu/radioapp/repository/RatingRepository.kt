package com.iu.radioapp.repository

import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.data.local.toDomain
import com.iu.radioapp.data.remote.s4feedback.FeedbackDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.OutboxEntry
import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.RatingEvent
import com.iu.radioapp.domain.map
import com.iu.radioapp.repository.mapping.toDomain
import com.iu.radioapp.repository.mapping.toRequestDto
import contract.common.RadioJson
import contract.s4feedback.RatingRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

class RatingRepository @Inject constructor(
    private val feedback: FeedbackDataSource,
    private val outboxDao: OutboxDao,
    private val clock: Clock,
) {

    suspend fun enqueue(rating: Rating): Outcome<Unit> {
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
        return Outcome.Success(Unit)
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

    suspend fun retry(idempotencyKey: String): Outcome<Unit> {
        outboxDao.reopenIfFailed(idempotencyKey)
        return Outcome.Success(Unit)
    }

    suspend fun getAggregate(showId: String): Outcome<RatingAggregate> =
        feedback.getAggregate(showId).map { it.toDomain() }

    suspend fun getRatingsSince(since: Instant, showId: String): Outcome<List<RatingEvent>> =
        feedback.getRatingsSince(since, showId).map { events -> events.map { it.toDomain() } }

    fun observeDeliveries(): Flow<List<OutboxEntry>> =
        outboxDao.observeAll().map { entries ->
            entries.filter { it.operation == OperationType.RATING }.map { it.toDomain() }
        }
}
