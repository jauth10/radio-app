package com.iu.radioapp.repository.mapping

import com.iu.radioapp.domain.Track
import contract.s2archive.TrackDto

// S2 does not return a duration; null means "unknown from this source".
fun TrackDto.toTrack(): Track = Track(
    trackId = trackId,
    artist = artist,
    title = title,
    album = album,
    coverUrl = coverUrl,
    durationSeconds = null,
    broadcastable = broadcastable,
)
