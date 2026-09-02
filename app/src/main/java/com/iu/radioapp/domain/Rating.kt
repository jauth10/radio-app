@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A listener's verdict on either the playlist or the presentation.
 *
 * What is being rated is expressed as [target] plus [referenceId] rather than as
 * two separate nullable references: for PLAYLIST the reference is a show id, for
 * HOST it is a host id. Exactly one reference exists in every case, which two
 * nullable fields could not express - they would allow three invalid states.
 *
 * [idempotencyKey] and [ratingId] work as in [SongRequest]: local identity first,
 * station identity only after a successful delivery. [value] is 1..5; the station
 * answers 422 for anything outside that range.
 */
data class Rating(
    val idempotencyKey: String,
    val ratingId: String?,
    val target: RatingTarget,
    val referenceId: String,
    val value: Int,
    val comment: String?,
    val createdAt: Instant,
    val listenerId: String,
)
