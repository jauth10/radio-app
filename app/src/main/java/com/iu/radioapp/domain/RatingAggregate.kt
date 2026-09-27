package com.iu.radioapp.domain

import kotlin.time.Instant

data class RatingAggregate(
    val averagePlaylistRating: Double,
    val playlistRatingCount: Int,
    val averageHostRating: Double,
    val hostRatingCount: Int,
    val windowStart: Instant,
    val windowEnd: Instant,
)
