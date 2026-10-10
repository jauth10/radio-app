package com.iu.radioapp.domain

/**
 * [host] is null when unhosted or served from the cache; [isStale] flags a cache older than five minutes;
 * [cause] is the failure behind a cache fallback and null for a live read.
 */
data class RatingContext(
    val show: Show,
    val host: Host?,
    val isStale: Boolean,
    val cause: Failure? = null,
)

/** What a rating of [target] points at: the show for the playlist, the host for the presentation. */
fun RatingContext.referenceIdFor(target: RatingTarget): String? = when (target) {
    RatingTarget.PLAYLIST -> show.showId
    RatingTarget.HOST -> host?.hostId
}
