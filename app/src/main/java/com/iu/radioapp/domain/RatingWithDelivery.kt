package com.iu.radioapp.domain

/** [rejectionReason] is set exactly when [status] is REJECTED. */
data class RatingWithDelivery(
    val rating: Rating,
    val status: DeliveryStatus,
    val rejectionReason: String?,
)
