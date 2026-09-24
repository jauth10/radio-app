package com.iu.radioapp.repository

import app.cash.turbine.test
import com.iu.radioapp.data.local.FakePlaybackHistoryDao
import com.iu.radioapp.data.local.FakeTrackCacheDao
import com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.ReadResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class TrackRepositoryTest {

    private val playout = FakePlayoutDataSource()
    private val trackCacheDao = FakeTrackCacheDao()
    private val historyDao = FakePlaybackHistoryDao()
    private val clock = MutableClock()
    private val repository = TrackRepository(playout, trackCacheDao, historyDao, clock)

    private suspend fun read(): ReadResult<NowPlaying?> =
        (repository.getCurrentPlayback() as Outcome.Success).value

    @Test
    fun `current playback comes back live and is written to cache and history`() = runTest {
        val result = read()

        assertTrue(result is ReadResult.Live)
        assertEquals("trk-1", result.value?.playback?.track?.trackId)
        assertEquals("host-1", result.value?.show?.host?.hostId)
        assertEquals(TEST_NOW, result.fetchedAt)
        assertEquals(TEST_NOW, trackCacheDao.findTrack("trk-1")?.fetchedAt)
        assertEquals(1, historyDao.count())
    }

    @Test
    fun `fields S1 does not deliver stay null`() = runTest {
        assertNull(read().value?.playback?.track?.broadcastable)
    }

    @Test
    fun `connection failure serves the cached playback with its original fetch time`() = runTest {
        read()
        clock.instant = TEST_NOW + 7.minutes
        playout.nextFailure = Failure.Connection

        val result = read()

        assertTrue(result is ReadResult.Cached)
        assertEquals(Failure.Connection, (result as ReadResult.Cached).cause)
        assertEquals("trk-1", result.value?.playback?.track?.trackId)
        assertEquals(210, result.value?.playback?.track?.durationSeconds)
        assertEquals("show-1", result.value?.show?.showId)
        assertEquals(TEST_NOW, result.fetchedAt)
    }

    @Test
    fun `server failure serves the cache and carries the cause`() = runTest {
        read()
        playout.nextFailure = Failure.Server

        assertEquals(Failure.Server, (read() as ReadResult.Cached).cause)
    }

    @Test
    fun `connection failure without a cache is passed on`() = runTest {
        playout.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), repository.getCurrentPlayback())
    }

    @Test
    fun `unauthorized is passed on even when a cache exists`() = runTest {
        read()
        playout.nextFailure = Failure.Unauthorized

        assertEquals(Outcome.Error(Failure.Unauthorized), repository.getCurrentPlayback())
    }

    @Test
    fun `talk segment comes back live and leaves cache and history untouched`() = runTest {
        read()
        val cachedBefore = trackCacheDao.tracks.value
        val historyBefore = historyDao.rows.value
        playout.currentTrack = null

        val result = read()

        assertTrue(result is ReadResult.Live)
        assertNull(result.value)
        assertEquals(cachedBefore, trackCacheDao.tracks.value)
        assertEquals(historyBefore, historyDao.rows.value)
    }

    @Test
    fun `cache still serves the last track after a talk segment`() = runTest {
        read()
        playout.currentTrack = null
        read()
        playout.nextFailure = Failure.Connection

        assertEquals("trk-1", read().value?.playback?.track?.trackId)
    }

    @Test
    fun `refreshHistory stores the station history newest first`() = runTest {
        assertEquals(Outcome.Success(Unit), repository.refreshHistory(limit = 10))

        repository.observeHistory().test {
            assertEquals(listOf("trk-1", "trk-2", "trk-3"), awaitItem().map { it.track.trackId })
        }
    }

    @Test
    fun `refreshHistory keeps the show of a playback that is already known`() = runTest {
        read()

        repository.refreshHistory(limit = 10)

        assertEquals("show-1", historyDao.rows.value.first { it.trackId == "trk-1" }.showId)
        assertEquals(3, historyDao.count())
    }

    @Test
    fun `refreshHistory passes a rejection on`() = runTest {
        val outcome = repository.refreshHistory(limit = -1)

        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
        assertEquals(0, historyDao.count())
    }

    @Test
    fun `refreshHistory passes a server failure on`() = runTest {
        playout.nextFailure = Failure.Server

        assertEquals(Outcome.Error(Failure.Server), repository.refreshHistory(limit = 10))
    }
}
