@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.domain.DeliveryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Verifies that kotlin.time.Instant survives a write and a read.
 *
 * Worth its own test because the type is stored as epoch milliseconds rather
 * than as text (see Converters), so the conversion is ours and not Room's - and
 * because the whole staleness and history logic depends on the value coming back
 * exactly as it went in.
 */
@RunWith(AndroidJUnit4::class)
class InstantPersistenceTest {

    private lateinit var database: RadioDatabase

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theFetchTimestampOfACachedTrackSurvivesTheRoundTrip() = runTest {
        val fetchedAt = Instant.parse("2026-09-13T12:34:56.789Z")
        val dao = database.trackCacheDao()

        dao.upsert(trackCacheEntity(fetchedAt = fetchedAt))

        assertEquals(fetchedAt, dao.findTrack("trk-1")?.fetchedAt)
    }

    @Test
    fun theStartTimeOfAnAiringSurvivesTheRoundTrip() = runTest {
        val startedAt = Instant.parse("2026-09-13T20:15:00.001Z")
        val dao = database.playbackHistoryDao()

        dao.appendAndTrim(historyEntity(startedAt = startedAt))

        assertEquals(startedAt, dao.observeLatest().first()?.startedAt)
    }

    /** null means "never attempted" and must not come back as an epoch value. */
    @Test
    fun aNullableInstantStaysNull() = runTest {
        val dao = database.outboxDao()

        val entryId = dao.insert(outboxEntity(lastAttemptAt = null))

        assertNull(dao.findEntry(entryId)?.lastAttemptAt)
    }

    @Test
    fun aWrittenAttemptTimeComesBackUnchanged() = runTest {
        val attemptedAt = Instant.parse("2026-09-13T06:00:00.250Z")
        val dao = database.outboxDao()
        val entryId = dao.insert(outboxEntity())

        dao.recordAttempt(
            entryId = entryId,
            attempts = 1,
            attemptedAt = attemptedAt,
            status = DeliveryStatus.OPEN,
        )

        assertEquals(attemptedAt, dao.findEntry(entryId)?.lastAttemptAt)
    }
}
