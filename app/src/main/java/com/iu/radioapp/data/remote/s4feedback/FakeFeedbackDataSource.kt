package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingResponse
import contract.s4feedback.RatingTarget
import contract.s4feedback.TimeWindow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * In-memory fake of [FeedbackDataSource].
 *
 * [getAggregate] and [getRatingsSince] read from fixed seed data plus whatever
 * [submitRating] added since - there is no real aggregation-by-show logic here,
 * just enough to exercise the success/failure paths against these fakes.
 */
@OptIn(ExperimentalTime::class)
class FakeFeedbackDataSource : FeedbackDataSource {

    var nextFailure: Failure? = null

    private val responsesByIdempotencyKey = mutableMapOf<String, RatingResponse>()

    private val ratings = mutableListOf(
        RatingEventDto(
            ratingId = "rat-1",
            target = RatingTarget.PLAYLIST,
            value = 4,
            comment = "Guter Mix",
            serverReceivedAt = Instant.parse("2026-08-28T09:00:00Z"),
            displayName = "Listener A",
        ),
        RatingEventDto(
            ratingId = "rat-2",
            target = RatingTarget.HOST,
            value = 5,
            comment = null,
            serverReceivedAt = Instant.parse("2026-08-28T09:30:00Z"),
            displayName = null,
        ),
    )

    private var nextRatingId = 3

    private fun consumeFailure(): Failure? = nextFailure.also { nextFailure = null }

    override suspend fun submitRating(idempotencyKey: String, rating: RatingRequest): Outcome<RatingResponse> {
        consumeFailure()?.let { return Outcome.Error(it) }

        responsesByIdempotencyKey[idempotencyKey]?.let { return Outcome.Success(it) }

        val ratingId = "rat-${nextRatingId++}"
        ratings.add(
            RatingEventDto(
                ratingId = ratingId,
                target = rating.target,
                value = rating.value,
                comment = rating.comment,
                serverReceivedAt = Clock.System.now(),
                displayName = null,
            )
        )
        val response = RatingResponse(ratingId = ratingId)
        responsesByIdempotencyKey[idempotencyKey] = response
        return Outcome.Success(response)
    }

    override suspend fun getAggregate(showId: String): Outcome<AggregateDto> {
        consumeFailure()?.let { return Outcome.Error(it) }
        val playlistRatings = ratings.filter { it.target == RatingTarget.PLAYLIST }
        val hostRatings = ratings.filter { it.target == RatingTarget.HOST }
        return Outcome.Success(
            AggregateDto(
                averagePlaylistRating = playlistRatings.map { it.value }.average(),
                playlistRatingCount = playlistRatings.size,
                averageHostRating = hostRatings.map { it.value }.average(),
                hostRatingCount = hostRatings.size,
                timeWindow = TimeWindow(
                    from = ratings.minOf { it.serverReceivedAt },
                    to = ratings.maxOf { it.serverReceivedAt },
                ),
            )
        )
    }

    override suspend fun getRatingsSince(since: Instant, showId: String): Outcome<List<RatingEventDto>> {
        consumeFailure()?.let { return Outcome.Error(it) }
        return Outcome.Success(ratings.filter { it.serverReceivedAt > since })
    }
}
