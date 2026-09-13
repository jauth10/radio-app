@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.RequestStatus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Shared setup for the instrumented database tests.
 *
 * The database is in memory: every test starts on an empty schema and nothing
 * survives the process, so the tests cannot influence each other through a file
 * left behind.
 *
 * All timestamps are derived from [TEST_EPOCH] rather than from the clock. The
 * data/local layer never reads the time itself, so a test does not have to work
 * around a moving "now" - and the expected values stay readable.
 */
internal fun createInMemoryDatabase(): RadioDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        RadioDatabase::class.java,
    ).build()

internal val TEST_EPOCH: Instant = Instant.parse("2026-09-13T12:00:00Z")

internal fun at(minutes: Int): Instant = TEST_EPOCH.plus(minutes.minutes)

internal fun trackCacheEntity(
    trackId: String = "trk-1",
    fetchedAt: Instant = TEST_EPOCH,
) = TrackCacheEntity(
    trackId = trackId,
    artist = "Sample Artist",
    title = "Sample Song",
    album = null,
    coverUrl = null,
    durationSeconds = 214,
    broadcastable = true,
    fetchedAt = fetchedAt,
)

internal fun historyEntity(
    trackId: String = "trk-1",
    startedAt: Instant = TEST_EPOCH,
) = PlaybackHistoryEntity(
    playbackId = playbackIdOf(trackId, startedAt),
    trackId = trackId,
    artist = "Sample Artist",
    title = "Sample Song",
    album = null,
    startedAt = startedAt,
    showId = "show-1",
)

internal fun outboxEntity(
    idempotencyKey: String = "key-1",
    attempts: Int = 0,
    lastAttemptAt: Instant? = null,
    status: DeliveryStatus = DeliveryStatus.OPEN,
) = OutboxEntity(
    idempotencyKey = idempotencyKey,
    operation = OperationType.SONG_REQUEST,
    payload = """{"trackId":"trk-1"}""",
    attempts = attempts,
    lastAttemptAt = lastAttemptAt,
    status = status,
)

internal fun songRequestEntity(
    idempotencyKey: String = "key-1",
    requestId: String? = null,
    status: RequestStatus = RequestStatus.PENDING,
    createdAt: Instant = TEST_EPOCH,
) = SongRequestEntity(
    idempotencyKey = idempotencyKey,
    requestId = requestId,
    trackId = "trk-1",
    trackTitle = "Sample Song",
    listenerId = "listener-1",
    message = null,
    createdAt = createdAt,
    status = status,
    rejectionReason = null,
    scheduledBroadcast = null,
)
