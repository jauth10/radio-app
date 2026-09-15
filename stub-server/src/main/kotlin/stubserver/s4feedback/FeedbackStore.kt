package stubserver.s4feedback

import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingResponse
import contract.s4feedback.RatingTarget
import contract.s4feedback.TimeWindow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import stubserver.common.HourlyLimiter

/**
 * In-memory data and business rules for S4 - Feedback & listener research.
 *
 * Models exactly one show/host pairing ([SEEDED_SHOW_ID] presented by
 * [SEEDED_HOST_ID], matching [stubserver.s1playout.PlayoutStore]'s current
 * track): any other showId comes back empty rather than mixing ratings in.
 */
@OptIn(ExperimentalTime::class)
object FeedbackStore {

    const val SEEDED_SHOW_ID = "show-1"
    const val SEEDED_HOST_ID = "host-1"

    sealed interface SubmitResult {
        data class Success(val response: RatingResponse) : SubmitResult
        /** Bad value or limit reached - 422. */
        data class Invalid(val reason: String) : SubmitResult
        /** Already rated - 409. */
        data class Conflict(val reason: String) : SubmitResult
    }

    private data class SeededRating(val event: RatingEventDto, val referenceId: String)

    private val limiter = HourlyLimiter()
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

    fun submit(idempotencyKey: String, rating: RatingRequest, receivedAt: Instant): SubmitResult {
        responsesByIdempotencyKey[idempotencyKey]?.let { return SubmitResult.Success(it) }

        if (rating.value !in 1..5) {
            return SubmitResult.Invalid("value must be 1..5")
        }

        val alreadyRatedKey = Triple(rating.listenerId, rating.target, rating.referenceId)
        if (alreadyRatedKey in ratedByListenerAndTarget) {
            return SubmitResult.Conflict("already rated")
        }
        if (!limiter.tryConsume(rating.listenerId, rating.timestamp)) {
            return SubmitResult.Invalid("hourly rating limit reached for listener ${rating.listenerId}")
        }

        val ratingId = "rat-${nextRatingId++}"
        ratings.add(
            SeededRating(
                RatingEventDto(
                    ratingId = ratingId,
                    target = rating.target,
                    value = rating.value,
                    comment = rating.comment,
                    serverReceivedAt = receivedAt,
                    displayName = null,
                ),
                referenceId = rating.referenceId,
            )
        )
        ratedByListenerAndTarget += alreadyRatedKey
        val response = RatingResponse(ratingId = ratingId)
        responsesByIdempotencyKey[idempotencyKey] = response
        return SubmitResult.Success(response)
    }

    fun aggregateFor(showId: String): AggregateDto {
        val now = Clock.System.now()
        if (showId != SEEDED_SHOW_ID) {
            return AggregateDto(0.0, 0, 0.0, 0, TimeWindow(from = now, to = now))
        }

        val playlistRatings = ratings
            .filter { it.referenceId == SEEDED_SHOW_ID && it.event.target == RatingTarget.PLAYLIST }
            .map { it.event }
        val hostRatings = ratings
            .filter { it.referenceId == SEEDED_HOST_ID && it.event.target == RatingTarget.HOST }
            .map { it.event }
        val relevant = playlistRatings + hostRatings

        return AggregateDto(
            averagePlaylistRating = playlistRatings.map { it.value }.average().takeIf { !it.isNaN() } ?: 0.0,
            playlistRatingCount = playlistRatings.size,
            averageHostRating = hostRatings.map { it.value }.average().takeIf { !it.isNaN() } ?: 0.0,
            hostRatingCount = hostRatings.size,
            timeWindow = TimeWindow(
                from = relevant.minOfOrNull { it.serverReceivedAt } ?: now,
                to = relevant.maxOfOrNull { it.serverReceivedAt } ?: now,
            ),
        )
    }

    fun since(since: Instant, showId: String): List<RatingEventDto> {
        if (showId != SEEDED_SHOW_ID) return emptyList()
        return ratings.filter { it.event.serverReceivedAt > since }.map { it.event }
    }
}
