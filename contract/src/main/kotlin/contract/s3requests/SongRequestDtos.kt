package contract.s3requests

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * S3 - Request management.
 *   POST /requests                 -> SongRequestResponse   (carries idempotency key)
 *   GET  /requests/{requestId}     -> SongRequestStatusDto
 *   GET  /requests?listenerId=     -> List<SongRequestOverviewDto>
 */

/** Request body for submitting a song request. Carries the 'Idempotency-Key' header. */
@Serializable
data class CreateSongRequestDto(
    val trackId: String,
    val listenerId: String,
    val displayName: String?,
    val message: String?,
    val timestamp: Instant,   // creation time on the DEVICE (matters for offline)
)

@Serializable
data class SongRequestResponse(
    val requestId: String,
    val status: RequestStatus,
    val scheduledBroadcast: Instant?,
)

@Serializable
data class SongRequestStatusDto(
    val status: RequestStatus,
    val reason: String?,
    val scheduledBroadcast: Instant?,
)

/**
 * Overview row in GET /requests?listenerId=.
 * 'trackTitle' holds the track name to display as a String (not an object).
 */
@Serializable
data class SongRequestOverviewDto(
    val requestId: String,
    val trackTitle: String,
    val status: RequestStatus,
)

/** Status of a request on the SERVER side (received -> ... -> decided). */
@Serializable
enum class RequestStatus {
    PENDING,
    IN_REVIEW,
    ACCEPTED,
    REJECTED,
}
