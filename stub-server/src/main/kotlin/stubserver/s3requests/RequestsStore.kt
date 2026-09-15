package stubserver.s3requests

import contract.s3requests.CreateSongRequestDto
import contract.s3requests.RequestStatus
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import stubserver.common.HourlyLimiter
import stubserver.s2archive.ArchiveStore

/**
 * In-memory data and business rules for S3 - Request management.
 *
 * Broadcastability is looked up in [ArchiveStore] rather than duplicated
 * here: S2's catalogue is the one place that flag lives, so the two stubs
 * cannot silently disagree about a track.
 */
@OptIn(ExperimentalTime::class)
object RequestsStore {

    sealed interface SubmitResult {
        data class Success(val response: SongRequestResponse) : SubmitResult
        /** Always a 422: not-broadcastable and limit-reached are both business rejections. */
        data class Rejected(val reason: String) : SubmitResult
    }

    private val limiter = HourlyLimiter()

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

    fun submit(idempotencyKey: String, request: CreateSongRequestDto): SubmitResult {
        responsesByIdempotencyKey[idempotencyKey]?.let { return SubmitResult.Success(it) }

        val track = ArchiveStore.tracks.find { it.trackId == request.trackId }
        if (track == null || !track.broadcastable) {
            return SubmitResult.Rejected("track not broadcastable: ${request.trackId}")
        }
        if (!limiter.tryConsume(request.listenerId, request.timestamp)) {
            return SubmitResult.Rejected("hourly request limit reached for listener ${request.listenerId}")
        }

        val requestId = "req-${nextRequestId++}"
        val response = SongRequestResponse(requestId = requestId, status = RequestStatus.PENDING, scheduledBroadcast = null)
        responsesByIdempotencyKey[idempotencyKey] = response
        statusByRequestId[requestId] = SongRequestStatusDto(status = response.status, reason = null, scheduledBroadcast = null)
        overviewByListenerId.getOrPut(request.listenerId) { mutableListOf() }.add(
            SongRequestOverviewDto(requestId = requestId, trackTitle = track.title, status = response.status)
        )
        return SubmitResult.Success(response)
    }

    fun statusOf(requestId: String): SongRequestStatusDto? = statusByRequestId[requestId]

    fun overviewFor(listenerId: String): List<SongRequestOverviewDto> = overviewByListenerId[listenerId].orEmpty()
}
