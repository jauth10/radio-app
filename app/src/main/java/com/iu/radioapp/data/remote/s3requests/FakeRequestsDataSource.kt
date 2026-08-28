package com.iu.radioapp.data.remote.s3requests

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.RequestStatus
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto

/**
 * In-memory fake of [RequestsDataSource].
 *
 * [submitRequest] keys its responses by [idempotencyKey]: the same key always
 * returns the same [SongRequestResponse] and never creates a second entry.
 */
class FakeRequestsDataSource : RequestsDataSource {

    var nextFailure: Failure? = null

    private val trackTitles = mapOf(
        "trk-1" to "Sample Song",
        "trk-2" to "Second Song",
        "trk-3" to "Third Song",
    )

    private val responsesByIdempotencyKey = mutableMapOf<String, SongRequestResponse>()
    private val statusByRequestId = mutableMapOf<String, SongRequestStatusDto>()
    private val overviewByListenerId = mutableMapOf<String, MutableList<SongRequestOverviewDto>>()

    private var nextRequestId = 1

    private fun consumeFailure(): Failure? = nextFailure.also { nextFailure = null }

    override suspend fun submitRequest(
        idempotencyKey: String,
        request: CreateSongRequestDto,
    ): Outcome<SongRequestResponse> {
        consumeFailure()?.let { return Outcome.Error(it) }

        responsesByIdempotencyKey[idempotencyKey]?.let { return Outcome.Success(it) }

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
        consumeFailure()?.let { return Outcome.Error(it) }
        val status = statusByRequestId[requestId]
            ?: return Outcome.Error(Failure.Rejected(reason = "unknown requestId: $requestId", retryable = false))
        return Outcome.Success(status)
    }

    override suspend fun getRequestsForListener(listenerId: String): Outcome<List<SongRequestOverviewDto>> {
        consumeFailure()?.let { return Outcome.Error(it) }
        return Outcome.Success(overviewByListenerId[listenerId].orEmpty())
    }
}
