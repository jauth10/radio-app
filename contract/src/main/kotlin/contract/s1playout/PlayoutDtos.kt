package contract.s1playout

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * S1 - Playout & schedule.
 *   GET  /playout/current      -> CurrentTrackDto
 *   GET  /playout/history?limit=      -> List<HistoryEntryDto>
 *   POST /playout/host/login   -> HostLoginResponse
 */

/**
 * Currently playing track.
 * hostId and hostName are nullable, since unhosted shows are the normal
 * case (domain relationship 0..1).
 */
@Serializable
data class CurrentTrackDto(
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
    val coverUrl: String?,
    val durationSeconds: Int,
    val startedAt: Instant,
    val showId: String,
    val hostId: String?,      // nullable: unhosted show
    val hostName: String?,    // nullable: unhosted show
)

/** One history entry (leaner than the current track). */
@Serializable
data class HistoryEntryDto(
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
    val startedAt: Instant,
)

/** Host login request. Carries NO idempotency key. */
@Serializable
data class HostLoginRequest(
    val hostCode: String,
    val deviceId: String,
)

/**
 * Host login response.
 * The sessionToken is sent afterwards as 'Authorization: Bearer {token}'
 * (HTTP) or as a query parameter (WebSocket).
 */
@Serializable
data class HostLoginResponse(
    val sessionToken: String,
    val validUntil: Instant,
    val hostId: String,
    val hostName: String,
)
