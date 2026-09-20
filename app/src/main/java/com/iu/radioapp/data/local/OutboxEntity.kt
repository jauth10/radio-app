@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.OperationType
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One pending write, waiting to be delivered to the station.
 *
 * The field names deliberately match the domain type OutboxEntry one to one, so
 * that the mapper stays a rename-free copy and a reviewer can compare both types
 * line by line. The column names are snake_case, which is the SQL convention and
 * independent of the Kotlin naming.
 *
 * [idempotencyKey] carries a unique index: it is generated once before the first
 * attempt and must identify exactly one queued write, however often delivery is
 * retried.
 *
 * [status] is indexed because the delivery worker only ever asks for one value of
 * it ("everything still OPEN").
 *
 * [rejectionReason] belongs to the same group as [status], [attempts] and
 * [lastAttemptAt]: it describes the delivery attempt, not the song request or the
 * rating. It holds business rejections only. The column is nullable because OPEN,
 * DELIVERED and FAILED have no reason - FAILED in particular must not borrow it
 * as a general error text.
 *
 * The enums are stored by name through Room's built-in enum support, see
 * Converters.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["status"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    val operation: OperationType,
    val payload: String,
    val attempts: Int,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Instant?,
    val status: DeliveryStatus,
    @ColumnInfo(name = "rejection_reason")
    val rejectionReason: String?,
)
