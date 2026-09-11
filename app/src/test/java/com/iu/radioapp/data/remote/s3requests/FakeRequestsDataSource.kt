package com.iu.radioapp.data.remote.s3requests

import com.iu.radioapp.data.remote.FailureSwitch
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.RequestStatus
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * In-memory fake of [RequestsDataSource].
 *
 * [submitRequest] keys its responses by [idempotencyKey]: the same key always
 * returns the same [SongRequestResponse] and never creates a second entry. A
 * request for a track outside [broadcastableTrackIds] is rejected the way the
 * real S3 would answer with 422.
 *
 * Two requests are pre-seeded already decided ("listener-seed": one accepted
 * with a scheduled broadcast, one rejected with a reason), since [submitRequest]
 * itself only ever produces PENDING - moving a request further is the
 * station's job, and this fake has no reviewer to simulate that.
 */
@OptIn(ExperimentalTime::class)
class FakeRequestsDataSource : RequestsDataSource {

    private val failures = FailureSwitch()

    var nextFailure: Failure?
        get() = failures.nextFailure
        set(value) { failures.nextFailure = value }

    var failureRepeatCount: Int
        get() = failures.times
        set(value) { failures.times = value }

    private val trackTitles = mapOf(
        "trk-1" to "Sample Song",
        "trk-2" to "Second Song",
        "trk-3" to "Third Song",
    )

    private val broadcastableTrackIds = setOf("trk-1", "trk-2")

    private val responsesByIdempotencyKey = mutableMapOf<String, SongRequestResponse>()

    private val statusByRequestId = mutableMapOf(
        "req-seed-accepted" to SongRequestStatusDto(
            status = RequestStatus.ACCEPTED,
            reason = null,
            scheduledBroadcast = Instant.parse("2026-08-29T18:00:00Z"),
        ),
        "req-seed-rejected" to SongRequestStatusDto(
            status = RequestStatus.REJECTED,
            reason = "Titel nicht im Bestand",
            scheduledBroadcast = null,
        ),
    )

    private val overviewByListenerId = mutableMapOf(
        "listener-seed" to mutableListOf(
            SongRequestOverviewDto("req-seed-accepted", "Sample Song", RequestStatus.ACCEPTED),
            SongRequestOverviewDto("req-seed-rejected", "Second Song", RequestStatus.REJECTED),
        ),
    )

    private var nextRequestId = 1

    override suspend fun submitRequest(
        idempotencyKey: String,
        request: CreateSongRequestDto,
    ): Outcome<SongRequestResponse> {
        failures.consume()?.let { return Outcome.Error(it) }

        responsesByIdempotencyKey[idempotencyKey]?.let { return Outcome.Success(it) }

        if (request.trackId !in broadcastableTrackIds) {
            return Outcome.Error(
                Failure.Rejected(reason = "track not broadcastable: ${request.trackId}", retryable = false)
            )
        }

        val requestId = "req-${nextRequestId++}"
        val response = SongRequestResponse(
            requestId = requestId,
            status = RequestStatus.PENDING,
            scheduledBroadcast = null,
        )
        responsesByIdempotencyKey[idempotencyKey] = response
        statusByRequestId[requestId] = SongRequestStatusDto(
            status = response.status,
            reason = null,
            scheduledBroadcast = null,
        )
        overviewByListenerId.getOrPut(request.listenerId) { mutableListOf() }.add(
            SongRequestOverviewDto(
                requestId = requestId,
                trackTitle = trackTitles[request.trackId] ?: request.trackId,
                status = response.status,
            )
        )
        return Outcome.Success(response)
    }

    override suspend fun getRequestStatus(requestId: String): Outcome<SongRequestStatusDto> {
        failures.consume()?.let { return Outcome.Error(it) }
        val status = statusByRequestId[requestId]
            ?: return Outcome.Error(Failure.Rejected(reason = "unknown requestId: $requestId", retryable = false))
        return Outcome.Success(status)
    }

    override suspend fun getRequestsForListener(listenerId: String): Outcome<List<SongRequestOverviewDto>> {
        failures.consume()?.let { return Outcome.Error(it) }
        return Outcome.Success(overviewByListenerId[listenerId].orEmpty())
    }
}
