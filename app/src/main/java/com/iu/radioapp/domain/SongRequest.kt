@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A listener asking for a track to be played.
 *
 * [idempotencyKey] is the local identity: it is generated before the first send
 * attempt and stays stable across every retry, so the station can recognise a
 * repeated delivery as the same request. [requestId] is the station's identity
 * and is therefore null until a delivery succeeded.
 *
 * [createdAt] is the moment the listener submitted the request on this device,
 * not the moment it reached the station - a request written while offline keeps
 * its original time when the outbox delivers it later.
 *
 * [trackTitle] and [scheduledBroadcast] are nullable because only some responses
 * carry them: the overview endpoint returns a display name instead of a track,
 * and a slot is only assigned once the station has scheduled the request.
 */
data class SongRequest(
    val idempotencyKey: String,
    val requestId: String?,
    val trackId: String,
    val trackTitle: String?,
    val listenerId: String,
    val message: String?,
    val createdAt: Instant,
    val status: RequestStatus,
    val rejectionReason: String?,
    val scheduledBroadcast: Instant?,
)
