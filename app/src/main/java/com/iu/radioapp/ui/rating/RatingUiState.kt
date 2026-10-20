package com.iu.radioapp.ui.rating

import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RatingContext
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RatingWithDelivery
import com.iu.radioapp.domain.RefusalReason

/**
 * [submitted] is the delivery of the rating sent from this screen, [undelivered] every other failed or rejected
 * rating of this target, whenever it was given. Both stay visible in every state.
 */
sealed interface RatingUiState {
    val submitted: SubmittedRating?
    val undelivered: List<RatingWithDelivery>

    data object Loading : RatingUiState {
        override val submitted: SubmittedRating? = null
        override val undelivered: List<RatingWithDelivery> = emptyList()
    }

    data class Content(
        val panel: RatingPanel,
        override val submitted: SubmittedRating?,
        override val undelivered: List<RatingWithDelivery> = emptyList(),
    ) : RatingUiState

    /** Talk segment: there is no show to rate (AK 2.3). */
    data class Empty(
        override val submitted: SubmittedRating?,
        override val undelivered: List<RatingWithDelivery> = emptyList(),
    ) : RatingUiState

    /** [panel] is null without any context; with a cached one, rating is still possible. */
    data class Offline(
        val panel: RatingPanel?,
        override val submitted: SubmittedRating?,
        override val undelivered: List<RatingWithDelivery> = emptyList(),
    ) : RatingUiState

    data class Error(
        val failure: Failure,
        val panel: RatingPanel?,
        override val submitted: SubmittedRating?,
        override val undelivered: List<RatingWithDelivery> = emptyList(),
    ) : RatingUiState
}

/**
 * [refusal] is the interactor's pre-check, [locked] the session lock after a queued rating (E30).
 * [submitRefusal] and [submitFailure] are what the last submission came back with.
 */
data class RatingPanel(
    val target: RatingTarget,
    val context: RatingContext,
    val refusal: RefusalReason?,
    val locked: Boolean,
    val value: Int?,
    val comment: String,
    val submitting: Boolean,
    val submitRefusal: RefusalReason?,
    val submitFailure: Failure?,
) {
    val canSubmit: Boolean get() = refusal == null && !locked && !submitting && value != null
}

data class SubmittedRating(
    val idempotencyKey: String,
    val status: DeliveryStatus,
    val rejectionReason: String?,
)
