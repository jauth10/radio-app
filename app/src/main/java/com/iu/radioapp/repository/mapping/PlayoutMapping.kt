package com.iu.radioapp.repository.mapping

import com.iu.radioapp.data.local.playbackIdOf
import com.iu.radioapp.domain.Host
import com.iu.radioapp.domain.HostSession
import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.Playback
import com.iu.radioapp.domain.Show
import com.iu.radioapp.domain.Track
import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import contract.s1playout.HostLoginResponse

// S1 does not know whether a track is broadcastable; null means "unknown from this source".
fun CurrentTrackDto.toNowPlaying(): NowPlaying = NowPlaying(
    playback = Playback(
        playbackId = playbackIdOf(trackId, startedAt),
        startedAt = startedAt,
        track = Track(
            trackId = trackId,
            artist = artist,
            title = title,
            album = album,
            coverUrl = coverUrl,
            durationSeconds = durationSeconds,
            broadcastable = null,
        ),
        showId = showId,
    ),
    show = Show(
        showId = showId,
        name = null,
        startsAt = null,
        endsAt = null,
        host = hostId?.let { Host(hostId = it, displayName = hostName ?: it) },
    ),
)

fun HistoryEntryDto.toPlayback(): Playback = Playback(
    playbackId = playbackIdOf(trackId, startedAt),
    startedAt = startedAt,
    track = Track(
        trackId = trackId,
        artist = artist,
        title = title,
        album = album,
        coverUrl = null,
        durationSeconds = null,
        broadcastable = null,
    ),
    showId = null,
)

fun HostLoginResponse.toHostSession(): HostSession = HostSession(
    host = Host(hostId = hostId, displayName = hostName),
    validUntil = validUntil,
)
