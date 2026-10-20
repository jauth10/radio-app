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

    // The first answer per key, rejections included: a retry gets the same status and body.
    private val resultsByIdempotencyKey = mutableMapOf<String, SubmitResult>()

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

    @Synchronized
    fun submit(idempotencyKey: String, request: CreateSongRequestDto, receivedAt: Instant): SubmitResult =
        resultsByIdempotencyKey.getOrPut(idempotencyKey) { evaluate(request, receivedAt) }

    private fun evaluate(request: CreateSongRequestDto, receivedAt: Instant): SubmitResult {
        val track = ArchiveStore.tracks.find { it.trackId == request.trackId }
        if (track == null || !track.broadcastable) {
            return SubmitResult.Rejected("Dieser Titel ist nicht sendefähig.")
        }
        if (!limiter.tryConsume(request.listenerId, receivedAt)) {
            return SubmitResult.Rejected("Du hast in dieser Stunde bereits das Wunschlimit erreicht.")
        }

        val requestId = "req-${nextRequestId++}"
        val response = SongRequestResponse(requestId = requestId, status = RequestStatus.PENDING, scheduledBroadcast = null)
        statusByRequestId[requestId] = SongRequestStatusDto(status = response.status, reason = null, scheduledBroadcast = null)
        overviewByListenerId.getOrPut(request.listenerId) { mutableListOf() }.add(
            SongRequestOverviewDto(requestId = requestId, trackTitle = track.title, status = response.status)
        )
        return SubmitResult.Success(response)
    }

    @Synchronized
    fun statusOf(requestId: String): SongRequestStatusDto? = statusByRequestId[requestId]

    @Synchronized
    fun overviewFor(listenerId: String): List<SongRequestOverviewDto> = overviewByListenerId[listenerId].orEmpty()
}
