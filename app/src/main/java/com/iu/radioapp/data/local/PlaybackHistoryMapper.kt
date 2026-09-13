@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import com.iu.radioapp.domain.Playback
import com.iu.radioapp.domain.Track
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Mapping between PlaybackHistoryEntity and the domain type Playback.
 */
fun PlaybackHistoryEntity.toDomain(): Playback = Playback(
    playbackId = playbackId,
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
    showId = showId,
)

fun Playback.toHistoryEntity(): PlaybackHistoryEntity = PlaybackHistoryEntity(
    playbackId = playbackId,
    trackId = track.trackId,
    artist = track.artist,
    title = track.title,
    album = track.album,
    startedAt = startedAt,
    showId = showId,
)

/**
 * The id an airing gets on this device, built from track and start time
 * (assumption 41): no S1 endpoint hands one out, and the pair is unique as long
 * as the same track does not start twice in the same instant.
 */
fun playbackIdOf(trackId: String, startedAt: Instant): String =
    "$trackId@${startedAt.toEpochMilliseconds()}"
