package com.iu.radioapp.repository.mapping

import com.iu.radioapp.domain.Rating
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.RatingEvent
import com.iu.radioapp.domain.RatingTarget
import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingTarget as RatingTargetDto

fun RatingTargetDto.toDomain(): RatingTarget = when (this) {
    RatingTargetDto.PLAYLIST -> RatingTarget.PLAYLIST
    RatingTargetDto.HOST -> RatingTarget.HOST
}

fun RatingTarget.toDto(): RatingTargetDto = when (this) {
    RatingTarget.PLAYLIST -> RatingTargetDto.PLAYLIST
    RatingTarget.HOST -> RatingTargetDto.HOST
}

fun Rating.toRequestDto(): RatingRequest = RatingRequest(
    target = target.toDto(),
    referenceId = referenceId,
    value = value,
    comment = comment,
    listenerId = listenerId,
    timestamp = createdAt,
)

fun AggregateDto.toDomain(): RatingAggregate = RatingAggregate(
    averagePlaylistRating = averagePlaylistRating,
    playlistRatingCount = playlistRatingCount,
    averageHostRating = averageHostRating,
    hostRatingCount = hostRatingCount,
    windowStart = timeWindow.from,
    windowEnd = timeWindow.to,
)

fun RatingEventDto.toDomain(): RatingEvent = RatingEvent(
    ratingId = ratingId,
    target = target.toDomain(),
    value = value,
    comment = comment,
    serverReceivedAt = serverReceivedAt,
    displayName = displayName,
)
