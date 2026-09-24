package com.iu.radioapp.domain

/** [deliveryStatus] is null when no outbox entry exists for the request. */
data class SongRequestWithDelivery(
    val request: SongRequest,
    val deliveryStatus: DeliveryStatus?,
)
