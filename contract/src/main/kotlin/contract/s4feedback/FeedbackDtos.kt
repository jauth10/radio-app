package contract.s4feedback

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * S4 - Feedback & listener research.
 *   POST /ratings                        -> RatingResponse  (idempotency key)
 *   GET  /ratings/aggregate?showId=      -> AggregateDto
 *   GET  /ratings?since=&showId=         -> List<RatingEventDto>  (POLLING fallback)
 *   WS   /events/ratings                 -> NEW_RATING / AGGREGATE_UPDATED
 */

/**
 * Request body for submitting a rating. Carries 'Idempotency-Key'.
 *
 * referenceId: for target=PLAYLIST this is the showId, for
 * target=HOST it is the hostId.
 * value: 1..5, outside that range -> 422.
 * timestamp: creation time on the DEVICE (offline-capable).
 */
@Serializable
data class RatingRequest(
    val target: RatingTarget,
    val referenceId: String,
    val value: Int,
    val comment: String?,
    val listenerId: String,
    val timestamp: Instant,
)

/**
 * Response for an accepted rating.
 * A rejection arrives server-side as 409/422 with ErrorDto; a success
 * response (2xx) already implies acceptance.
 */
@Serializable
data class RatingResponse(
    val ratingId: String,
)

/**
 * Aggregate values for a show.
 */
@Serializable
data class AggregateDto(
    val averagePlaylistRating: Double,
    val playlistRatingCount: Int,
    val averageHostRating: Double,
    val hostRatingCount: Int,
    val timeWindow: TimeWindow,
)

@Serializable
data class TimeWindow(
    val from: Instant,
    val to: Instant,
)

/**
 * A rating event - identical shape for the WebSocket push AND the polling
 * response (GET /ratings?since=). One shape, two transport paths.
 *
 * Important for latency measurement: serverReceivedAt is the RECEIVE time
 * on the SERVER, not the client field from the request. Only that way does
 * t1 - serverReceivedAt measure the real end-to-end latency and not the
 * device's clock drift.
 */
@Serializable
data class RatingEventDto(
    val ratingId: String,
    val target: RatingTarget,
    val value: Int,
    val comment: String?,
    val serverReceivedAt: Instant,   // server receive time (latency measurement point)
    val displayName: String?,
)

@Serializable
enum class RatingTarget {
    PLAYLIST,
    HOST,
}

/** Message types on the WebSocket channel. */
@Serializable
enum class EventType {
    NEW_RATING,
    AGGREGATE_UPDATED,
}
