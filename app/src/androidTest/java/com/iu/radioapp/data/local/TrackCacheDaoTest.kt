@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

@RunWith(AndroidJUnit4::class)
class TrackCacheDaoTest {

    private lateinit var database: RadioDatabase
    private lateinit var trackCacheDao: TrackCacheDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        trackCacheDao = database.trackCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aRefetchedTrackReplacesTheStoredRowAndItsFetchTime() = runTest {
        trackCacheDao.upsert(trackCacheEntity(fetchedAt = at(0)))
        trackCacheDao.upsert(trackCacheEntity(fetchedAt = at(6)).copy(broadcastable = null, album = "Fake Album"))

        val cached = trackCacheDao.findTrack("trk-1")

        assertEquals(at(6), cached?.fetchedAt)
        assertNull(cached?.broadcastable)
        assertEquals("Fake Album", cached?.album)
    }

    @Test
    fun aTrackThatWasNeverFetchedIsNotInTheCache() = runTest {
        trackCacheDao.upsert(trackCacheEntity(trackId = "trk-1"))

        assertNull(trackCacheDao.findTrack("trk-2"))
    }
}
