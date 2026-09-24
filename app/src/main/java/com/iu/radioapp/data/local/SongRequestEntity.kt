@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iu.radioapp.domain.RequestStatus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The listener's own song requests, as far as this device knows them.
 *
 * The row is written when the request is queued, not when it is delivered: that
 * is what lets the screen show the request immediately ("pending") while the
 * outbox is still working. Delivery then fills in what only the station can
 * decide - [requestId], [status], [scheduledBroadcast], [rejectionReason].
 *
 * Two identities, deliberately kept apart. [idempotencyKey] is the local one and
 * therefore the primary key: it exists from the first moment and never changes.
 * [requestId] is the station's and stays null until a delivery succeeded; its
 * unique index rejects the same station id appearing on two rows.
 *
 * Careful with [status]: this is com.iu.radioapp.domain.RequestStatus, the
 * station-side progress of the request. It is NOT DeliveryStatus (how far this
 * device got with sending it), and it is not contract.s3requests.RequestStatus,
 * the wire enum of the same name - DTOs stop at the data source.
 */
@Entity(
    tableName = "song_request",
    indices = [
        Index(value = ["request_id"], unique = true),
        Index(value = ["listener_id"]),
    ],
)
data class SongRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "request_id")
    val requestId: String?,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "track_title")
    val trackTitle: String?,
    @ColumnInfo(name = "listener_id")
    val listenerId: String,
    val message: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    val status: RequestStatus,
    @ColumnInfo(name = "rejection_reason")
    val rejectionReason: String?,
    @ColumnInfo(name = "scheduled_broadcast")
    val scheduledBroadcast: Instant?,
)
