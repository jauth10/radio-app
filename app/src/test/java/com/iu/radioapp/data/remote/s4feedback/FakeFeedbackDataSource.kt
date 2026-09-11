package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.data.remote.FailureSwitch
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
 * The fixture models exactly one show/host pairing: [SEEDED_SHOW_ID] presented
 * by [SEEDED_HOST_ID] (matching [com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource]'s
 * current track). [RatingEventDto] carries no reference back to the show or
 * host it was submitted for - that association only exists internally here via
 * [SeededRating.referenceId] - so [getAggregate] and [getRatingsSince] can
 * still tell "belongs to this show" from "belongs elsewhere" apart, and any
 * other showId comes back empty rather than mixing ratings in.
 *
 * [clock] defaults to [Clock.System] but can be replaced with a fixed clock in
 * a test, since real time would otherwise make submitted ratings' timestamps
 * non-reproducible.
 */
@OptIn(ExperimentalTime::class)
class FakeFeedbackDataSource(private val clock: Clock = Clock.System) : FeedbackDataSource {

    private val failures = FailureSwitch()

    var nextFailure: Failure?
        get() = failures.nextFailure
        set(value) { failures.nextFailure = value }

    var failureRepeatCount: Int
        get() = failures.times
        set(value) { failures.times = value }

    private data class SeededRating(val event: RatingEventDto, val referenceId: String)

    private val responsesByIdempotencyKey = mutableMapOf<String, RatingResponse>()
    private val ratedByListenerAndTarget = mutableSetOf<Triple<String, RatingTarget, String>>()

    private val ratings = mutableListOf(
        SeededRating(
            RatingEventDto(
                ratingId = "rat-1",
                target = RatingTarget.PLAYLIST,
                value = 4,
                comment = "Guter Mix",
                serverReceivedAt = Instant.parse("2026-08-28T09:00:00Z"),
                displayName = "Listener A",
            ),
            referenceId = SEEDED_SHOW_ID,
        ),
        SeededRating(
            RatingEventDto(
                ratingId = "rat-2",
                target = RatingTarget.HOST,
                value = 5,
                comment = null,
                serverReceivedAt = Instant.parse("2026-08-28T09:30:00Z"),
                displayName = null,
            ),
            referenceId = SEEDED_HOST_ID,
        ),
    )

    private var nextRatingId = 3

    override suspend fun submitRating(idempotencyKey: String, rating: RatingRequest): Outcome<RatingResponse> {
        failures.consume()?.let { return Outcome.Error(it) }

        responsesByIdempotencyKey[idempotencyKey]?.let { return Outcome.Success(it) }

        if (rating.value !in 1..5) {
            return Outcome.Error(Failure.Rejected(reason = "value must be 1..5", retryable = false))
        }

        val alreadyRatedKey = Triple(rating.listenerId, rating.target, rating.referenceId)
        if (alreadyRatedKey in ratedByListenerAndTarget) {
            return Outcome.Error(Failure.Rejected(reason = "already rated", retryable = false))
        }

        val ratingId = "rat-${nextRatingId++}"
        ratings.add(
            SeededRating(
                RatingEventDto(
                    ratingId = ratingId,
                    target = rating.target,
                    value = rating.value,
                    comment = rating.comment,
                    serverReceivedAt = clock.now(),
                    displayName = null,
                ),
                referenceId = rating.referenceId,
            )
        )
        ratedByListenerAndTarget += alreadyRatedKey
        val response = RatingResponse(ratingId = ratingId)
        responsesByIdempotencyKey[idempotencyKey] = response
        return Outcome.Success(response)
    }

    override suspend fun getAggregate(showId: String): Outcome<AggregateDto> {
        failures.consume()?.let { return Outcome.Error(it) }

        if (showId != SEEDED_SHOW_ID) {
            return Outcome.Success(
                AggregateDto(
                    averagePlaylistRating = 0.0,
                    playlistRatingCount = 0,
                    averageHostRating = 0.0,
                    hostRatingCount = 0,
                    timeWindow = TimeWindow(from = clock.now(), to = clock.now()),
                )
            )
        }

        val playlistRatings = ratings.filter { it.referenceId == SEEDED_SHOW_ID && it.event.target == RatingTarget.PLAYLIST }
            .map { it.event }
        val hostRatings = ratings.filter { it.referenceId == SEEDED_HOST_ID && it.event.target == RatingTarget.HOST }
            .map { it.event }
        val relevant = playlistRatings + hostRatings

        return Outcome.Success(
            AggregateDto(
                averagePlaylistRating = playlistRatings.map { it.value }.average().takeIf { !it.isNaN() } ?: 0.0,
                playlistRatingCount = playlistRatings.size,
                averageHostRating = hostRatings.map { it.value }.average().takeIf { !it.isNaN() } ?: 0.0,
                hostRatingCount = hostRatings.size,
                timeWindow = TimeWindow(
                    from = relevant.minOfOrNull { it.serverReceivedAt } ?: clock.now(),
                    to = relevant.maxOfOrNull { it.serverReceivedAt } ?: clock.now(),
                ),
            )
        )
    }

    override suspend fun getRatingsSince(since: Instant, showId: String): Outcome<List<RatingEventDto>> {
        failures.consume()?.let { return Outcome.Error(it) }
        if (showId != SEEDED_SHOW_ID) return Outcome.Success(emptyList())
        return Outcome.Success(
            ratings.filter { it.event.serverReceivedAt > since }.map { it.event }
        )
    }

    companion object {
        const val SEEDED_SHOW_ID = "show-1"
        const val SEEDED_HOST_ID = "host-1"
    }
}
