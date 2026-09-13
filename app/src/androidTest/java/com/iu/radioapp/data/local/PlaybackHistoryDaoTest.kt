@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 * Instrumented tests for the playback history and its 50 entry limit
 */
@RunWith(AndroidJUnit4::class)
class PlaybackHistoryDaoTest {

    private lateinit var database: RadioDatabase
    private lateinit var historyDao: PlaybackHistoryDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        historyDao = database.playbackHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * The 51st entry must not make the table grow: it pushes the oldest one out.
     * "Oldest" is the smallest startedAt, not the row inserted first - the
     * station's history can be read out of order.
     */
    @Test
    fun theFiftyFirstEntryEvictsTheOldestOne() = runTest {
        repeat(50) { index ->
            historyDao.appendAndTrim(historyEntity(trackId = "trk-$index", startedAt = at(index)))
        }
        assertEquals(50, historyDao.count())

        historyDao.appendAndTrim(historyEntity(trackId = "trk-50", startedAt = at(50)))

        assertEquals(50, historyDao.count())
        val trackIds = historyDao.observeHistory().first().map { it.trackId }
        assertFalse("the oldest entry should have been evicted", "trk-0" in trackIds)
        assertTrue("the newest entry should be present", "trk-50" in trackIds)
    }

    @Test
    fun anEntryOlderThanAllFiftyIsNotKept() = runTest {
        repeat(50) { index ->
            historyDao.appendAndTrim(historyEntity(trackId = "trk-$index", startedAt = at(index + 1)))
        }

        historyDao.appendAndTrim(historyEntity(trackId = "trk-ancient", startedAt = at(0)))

        assertEquals(50, historyDao.count())
        val trackIds = historyDao.observeHistory().first().map { it.trackId }
        assertFalse("an entry older than the limit should not survive", "trk-ancient" in trackIds)
    }

    @Test
    fun theHistoryIsOrderedNewestFirstAndTheNewestRowIsWhatIsOnAir() = runTest {
        historyDao.appendAndTrim(historyEntity(trackId = "trk-old", startedAt = at(0)))
        historyDao.appendAndTrim(historyEntity(trackId = "trk-new", startedAt = at(5)))

        assertEquals(
            listOf("trk-new", "trk-old"),
            historyDao.observeHistory().first().map { it.trackId },
        )
        assertEquals("trk-new", historyDao.observeLatest().first()?.trackId)
    }

    /**
     * Reading the station's history twice must not duplicate anything: the
     * playback id is formed from track and start time, so the same airing is the
     * same row.
     */
    @Test
    fun readingTheSameAiringTwiceUpdatesInsteadOfDuplicating() = runTest {
        historyDao.appendAndTrim(historyEntity(trackId = "trk-1", startedAt = at(1)))
        historyDao.appendAndTrim(historyEntity(trackId = "trk-1", startedAt = at(1)))

        assertEquals(1, historyDao.count())
    }
}
