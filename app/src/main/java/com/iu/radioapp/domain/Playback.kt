@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One airing of a track: this track, started at this moment.
 *
 * [track] is embedded rather than referenced by id because both endpoints that
 * produce a Playback return the track fields inline. Storing only a trackId here
 * would force the UI to look up data the same response already carried.
 *
 * [showId] is nullable: the playout history returns no show reference.
 */
data class Playback(
    val playbackId: String,
    val startedAt: Instant,
    val track: Track,
    val showId: String?,
)
