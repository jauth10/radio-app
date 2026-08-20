package com.iu.radioapp.domain

/**
 * How far an [OutboxEntry] has come on THIS DEVICE.
 *
 * OPEN deliberately avoids the word "pending": on the station's side
 * [RequestStatus.PENDING] means "received, not yet reviewed", here it would mean
 * "not yet delivered". Two different facts must not share a name.
 *
 * REJECTED is terminal and is never retried automatically. FAILED is reached
 * after five unsuccessful attempts and offers a manual retry.
 */
enum class DeliveryStatus {
    OPEN,
    DELIVERED,
    REJECTED,
    FAILED,
}
