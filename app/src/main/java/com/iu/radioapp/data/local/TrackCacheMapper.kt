@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import com.iu.radioapp.domain.Track
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Mapping between TrackCacheEntity and the domain type Track.
 *
 * The two are separate types on purpose: the domain stays free of Room
 * annotations, and the cache may carry columns the domain does not know.
 * [fetchedAt] is exactly such a column - it is a fact about the cache, not about
 * the track, which is why Track has no field for it and the staleness check sits
 * in the interactor instead of here.
 *
 * [fetchedAt] is a parameter rather than a clock read: this layer never asks what
 * time it is, so every write stays reproducible in a test.
 */
fun TrackCacheEntity.toDomain(): Track = Track(
    trackId = trackId,
    artist = artist,
    title = title,
    album = album,
    coverUrl = coverUrl,
    durationSeconds = durationSeconds,
    broadcastable = broadcastable,
)

fun Track.toCacheEntity(fetchedAt: Instant): TrackCacheEntity = TrackCacheEntity(
    trackId = trackId,
    artist = artist,
    title = title,
    album = album,
    coverUrl = coverUrl,
    durationSeconds = durationSeconds,
    broadcastable = broadcastable,
    fetchedAt = fetchedAt,
)
