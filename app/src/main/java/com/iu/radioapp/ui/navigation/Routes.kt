package com.iu.radioapp.ui.navigation

import com.iu.radioapp.domain.Track
import kotlinx.serialization.Serializable

@Serializable
data object ProgramRoute

@Serializable
data object MyRequestsRoute

@Serializable
data object TrackSearchRoute

/** Only the displayed fields travel; the request screen completes the rest from the archive. */
@Serializable
data class SongRequestRoute(
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
) {
    fun toTrack() = Track(
        trackId = trackId,
        artist = artist,
        title = title,
        album = album,
        coverUrl = null,
        durationSeconds = null,
        broadcastable = null,
    )

    companion object {
        fun of(track: Track) = SongRequestRoute(track.trackId, track.artist, track.title, track.album)
    }
}

@Serializable
data object RatingRoute

@Serializable
data object HostEntryRoute
