package com.iu.radioapp.domain

/**
 * A piece of music, independent of any particular airing.
 *
 * [durationSeconds] and [broadcastable] are nullable because no single endpoint
 * supplies both: the playout system knows the duration of what is on air, the
 * archive knows whether a track may be requested. Null therefore means "not known
 * from the source this instance came from", never "the track has no duration".
 */
data class Track(
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
    val coverUrl: String?,
    val durationSeconds: Int?,
    val broadcastable: Boolean?,
)
