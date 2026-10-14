package com.iu.radioapp.domain

sealed interface HostFeed {

    data object SignedOut : HostFeed

    /** The session ran out or was rejected; a new login is needed. */
    data object SessionExpired : HostFeed

    /** [ratings] newest first; [showId] is null when no show was known at the start. */
    data class Active(
        val ratings: List<ReceivedRating>,
        val aggregate: RatingAggregate?,
        val connection: ConnectionState,
        val showId: String?,
    ) : HostFeed
}
