package com.iu.radioapp.ui.host

import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.ReceivedRating

/** Decides between login and host view; the host view itself is [HostFeedUiState]. */
sealed interface HostAccess {
    data object Checking : HostAccess
    data class SignedOut(val sessionExpired: Boolean) : HostAccess
    data object SignedIn : HostAccess
}

/** Empty means no rating has arrived yet; Offline and Error hide the numbers but keep the list. */
sealed interface HostFeedUiState {

    data object Loading : HostFeedUiState

    data class Content(val view: HostFeedView) : HostFeedUiState

    data class Empty(val view: HostFeedView) : HostFeedUiState

    data class Offline(val view: HostFeedView) : HostFeedUiState

    data class Error(val failure: Failure, val view: HostFeedView) : HostFeedUiState
}

/** [numbers] is null whenever they must not be shown (talk segment, no current show data). */
data class HostFeedView(
    val ratings: List<ReceivedRating>,
    val connection: ConnectionState,
    val showKnown: Boolean,
    val numbers: RatingAggregate?,
    val talkSegment: Boolean,
)
