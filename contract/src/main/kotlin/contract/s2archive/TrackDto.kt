package contract.s2archive

import kotlinx.serialization.Serializable

/**
 * S2 - Music archive.
 *   GET /archive/tracks?q=&limit=   -> List<TrackDto>
 *   GET /archive/tracks/{trackId}   -> TrackDto
 *
 * Note: list endpoints without pagination return a list directly
 * (no wrapper object such as TrackSearchResponse), to keep the structure
 * consistent project-wide.
 */
@Serializable
data class TrackDto(
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
    val coverUrl: String?,
    val broadcastable: Boolean,
)
