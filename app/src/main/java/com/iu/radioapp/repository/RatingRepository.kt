package com.iu.radioapp.repository

import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.data.local.toDomain
import com.iu.radioapp.data.remote.s4feedback.FeedbackDataSource
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
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

    /** Returns the first technical failure, so the worker knows a retry is due. */
    suspend fun deliverOpen(): Outcome<Unit> {
        var technicalFailure: Failure? = null
        for (entry in outboxDao.openEntries().filter { it.operation == OperationType.RATING }) {
            val dto = RadioJson.decodeFromString<RatingRequest>(entry.payload)
            when (val outcome = feedback.submitRating(entry.idempotencyKey, dto)) {
                is Outcome.Success -> outboxDao.markDelivered(entry.id)
                is Outcome.Error -> when (val failure = outcome.failure) {
                    // Never re-sent on its own, whatever retryable says.
                    is Failure.Rejected -> outboxDao.markRejected(entry.id, failure.reason)
                    else -> {
                        outboxDao.recordFailedAttempt(entry, clock.now())
                        technicalFailure = technicalFailure ?: failure
                    }
                }
            }
            // Without a connection the remaining entries would only burn attempts.
            if (technicalFailure is Failure.Connection) break
        }
        return technicalFailure?.let { Outcome.Error(it) } ?: Outcome.Success(Unit)
    }

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
