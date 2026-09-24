package com.iu.radioapp.repository

import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import kotlin.time.Clock
import kotlin.time.Instant

internal object DeliveryPolicy {
    const val MAX_ATTEMPTS_PER_OPENING = 5

    // attempts keeps counting across manual reopenings (E27), hence the modulo.
    fun statusAfterFailure(attempts: Int): DeliveryStatus =
        if (attempts % MAX_ATTEMPTS_PER_OPENING == 0) DeliveryStatus.FAILED else DeliveryStatus.OPEN
}

internal suspend fun OutboxDao.recordFailedAttempt(entry: OutboxEntity, attemptedAt: Instant) {
    val attempts = entry.attempts + 1
    recordAttempt(entry.id, attempts, attemptedAt, DeliveryPolicy.statusAfterFailure(attempts))
}

internal suspend fun OutboxDao.reopenIfFailed(idempotencyKey: String) {
    val entry = findByIdempotencyKey(idempotencyKey) ?: return
    if (entry.status == DeliveryStatus.FAILED) reopen(entry.id)
}

internal suspend fun <T> OutboxDao.deliverOpenEntries(
    operation: OperationType,
    clock: Clock,
    send: suspend (OutboxEntity) -> Outcome<T>,
    onDelivered: suspend (OutboxEntity, T) -> Unit,
    onRejected: suspend (OutboxEntity, reason: String) -> Unit,
): Outcome<Unit> {
    var technicalFailure: Failure? = null
    for (entry in openEntries().filter { it.operation == operation }) {
        when (val outcome = send(entry)) {
            is Outcome.Success -> onDelivered(entry, outcome.value)
            is Outcome.Error -> when (val failure = outcome.failure) {
                // Never re-sent on its own, whatever retryable says.
                is Failure.Rejected -> onRejected(entry, failure.reason)
                else -> {
                    recordFailedAttempt(entry, clock.now())
                    technicalFailure = technicalFailure ?: failure
                    // Without a connection the remaining entries would only burn attempts.
                    if (failure is Failure.Connection) break
                }
            }
        }
    }
    return technicalFailure?.let { Outcome.Error(it) } ?: Outcome.Success(Unit)
}
