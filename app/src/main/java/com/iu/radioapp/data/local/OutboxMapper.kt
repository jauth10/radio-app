package com.iu.radioapp.data.local

import com.iu.radioapp.domain.OutboxEntry

/**
 * Mapping between OutboxEntity and the domain type OutboxEntry.
 *
 * Field for field identical - which is the point: the outbox is the one place
 * where the domain and the table describe the same thing, so any difference
 * between them would be an error rather than a design.
 *
 * [OutboxEntry.id] is 0 for an entry that has not been stored yet; Room fills in
 * the generated row id on insert.
 */
fun OutboxEntity.toDomain(): OutboxEntry = OutboxEntry(
    id = id,
    idempotencyKey = idempotencyKey,
    operation = operation,
    payload = payload,
    attempts = attempts,
    lastAttemptAt = lastAttemptAt,
    status = status,
)

fun OutboxEntry.toEntity(): OutboxEntity = OutboxEntity(
    id = id,
    idempotencyKey = idempotencyKey,
    operation = operation,
    payload = payload,
    attempts = attempts,
    lastAttemptAt = lastAttemptAt,
    status = status,
)
