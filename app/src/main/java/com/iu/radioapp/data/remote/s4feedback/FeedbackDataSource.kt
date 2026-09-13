package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.domain.Outcome
import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingResponse
import kotlin.time.Instant

/**
 * S4 - Feedback & listener research. See [contract.s4feedback] for the wire
 * format.
 *
 * [getRatingsSince] is the polling fallback for the WebSocket channel (RAD-15);
 * it is not itself a WebSocket client.
 */
interface FeedbackDataSource {

    suspend fun submitRating(idempotencyKey: String, rating: RatingRequest): Outcome<RatingResponse>

    suspend fun getAggregate(showId: String): Outcome<AggregateDto>

    suspend fun getRatingsSince(since: Instant, showId: String): Outcome<List<RatingEventDto>>
}
