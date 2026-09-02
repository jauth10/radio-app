package com.iu.radioapp.domain

/**
 * How far a song request has come on the STATION's side.
 *
 * PENDING means "received, not yet reviewed". It is not to be confused with
 * [DeliveryStatus.OPEN], which is about this device not having delivered the
 * request yet.
 */
enum class RequestStatus {
    PENDING,
    IN_REVIEW,
    ACCEPTED,
    REJECTED,
}
