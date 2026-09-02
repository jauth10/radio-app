@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One pending write, waiting to be delivered to the station.
 *
 * Song requests and ratings always take this path, even with a working
 * connection, so that there is a single delivery mechanism instead of two.
 *
 * [payload] holds the already serialised request body as text. Keeping it opaque
 * is what lets one outbox carry both operations without the delivery worker
 * needing to know either of them; [operation] tells it where to send the payload.
 *
 * [lastAttemptAt] is null while no attempt has been made yet. After five failed
 * attempts the status becomes FAILED and the entry stays visible for a manual
 * retry - it is never dropped silently.
 */
data class OutboxEntry(
    val id: Long,
    val idempotencyKey: String,
    val operation: OperationType,
    val payload: String,
    val attempts: Int,
    val lastAttemptAt: Instant?,
    val status: DeliveryStatus,
)
