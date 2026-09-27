package com.iu.radioapp.domain

import kotlin.time.Instant

data class RatingEvent(
    val ratingId: String,
    val target: RatingTarget,
    val value: Int,
    val comment: String?,
    val serverReceivedAt: Instant,
    val displayName: String?,
)
