package com.iu.radioapp.repository

import com.iu.radioapp.data.local.OutboxDao
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.domain.DeliveryStatus
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
