package com.iu.radioapp.repository

import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

val TEST_NOW: Instant = Instant.parse("2026-09-20T12:00:00Z")

/** With a [scheduler], the clock also moves with the test's virtual time, in step with delay. */
@OptIn(ExperimentalCoroutinesApi::class)
class MutableClock(
    var instant: Instant = TEST_NOW,
    private val scheduler: TestCoroutineScheduler? = null,
) : Clock {
    override fun now(): Instant = instant + (scheduler?.currentTime ?: 0L).milliseconds
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
    displayName = null,
)

val <T> Outcome<T>.successValue: T get() = (this as Outcome.Success).value

suspend fun <T> Flow<Outcome<T>>.firstValue(): T = first().successValue
