package com.iu.radioapp.data.local

import com.iu.radioapp.domain.SongRequest

/**
 * Mapping between SongRequestEntity and the domain type SongRequest.
 *
 * Both sides use com.iu.radioapp.domain.RequestStatus. The wire enum of the same
 * name, contract.s3requests.RequestStatus, does not appear here and must not:
 * translating it is the data source's job, this layer only knows entities and
 * domain.
 */
fun SongRequestEntity.toDomain(): SongRequest = SongRequest(
    idempotencyKey = idempotencyKey,
    requestId = requestId,
    trackId = trackId,
    trackTitle = trackTitle,
    listenerId = listenerId,
    message = message,
    createdAt = createdAt,
    status = status,
    rejectionReason = rejectionReason,
    scheduledBroadcast = scheduledBroadcast,
)

fun SongRequest.toEntity(): SongRequestEntity = SongRequestEntity(
    idempotencyKey = idempotencyKey,
    requestId = requestId,
    trackId = trackId,
    trackTitle = trackTitle,
    listenerId = listenerId,
    message = message,
    createdAt = createdAt,
    status = status,
    rejectionReason = rejectionReason,
    scheduledBroadcast = scheduledBroadcast,
)
