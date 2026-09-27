package com.iu.radioapp.repository

import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import kotlin.time.Clock
import kotlin.time.Instant

val TEST_NOW: Instant = Instant.parse("2026-09-20T12:00:00Z")

class MutableClock(var instant: Instant = TEST_NOW) : Clock {
    override fun now(): Instant = instant
}

fun songRequest(
    idempotencyKey: String = "key-1",
    trackId: String = "trk-1",
    listenerId: String = "listener-1",
) = SongRequest(
    idempotencyKey = idempotencyKey,
    requestId = null,
    trackId = trackId,
    trackTitle = "Sample Song",
    listenerId = listenerId,
    message = null,
    createdAt = TEST_NOW,
    status = RequestStatus.PENDING,
    rejectionReason = null,
    scheduledBroadcast = null,
)

fun rating(
    idempotencyKey: String = "key-1",
    value: Int = 4,
    target: RatingTarget = RatingTarget.PLAYLIST,
    referenceId: String = "show-1",
) = Rating(
    idempotencyKey = idempotencyKey,
    ratingId = null,
    target = target,
    referenceId = referenceId,
    value = value,
    comment = null,
    createdAt = TEST_NOW,
    listenerId = "listener-1",
)
