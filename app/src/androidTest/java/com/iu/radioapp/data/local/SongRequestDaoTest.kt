@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.iu.radioapp.domain.RequestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

@RunWith(AndroidJUnit4::class)
class SongRequestDaoTest {

    private lateinit var database: RadioDatabase
    private lateinit var songRequestDao: SongRequestDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        songRequestDao = database.songRequestDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun myRequestsListOnlyTheOwnListenerNewestFirst() = runTest {
        songRequestDao.upsertAll(
            listOf(
                songRequestEntity(idempotencyKey = "key-old", createdAt = at(1)),
                songRequestEntity(idempotencyKey = "key-new", createdAt = at(5)),
                songRequestEntity(idempotencyKey = "key-other", createdAt = at(3)).copy(listenerId = "listener-2"),
            )
        )

        val requests = songRequestDao.observeRequests("listener-1").first()

        assertEquals(listOf("key-new", "key-old"), requests.map { it.idempotencyKey })
    }

    @Test
    fun aStatusFromTheStationReachesTheObserverAndKeepsTheRest() = runTest {
        val stored = songRequestEntity(requestId = "req-1").copy(message = "Bitte laut")
        songRequestDao.upsertAll(listOf(stored))

        songRequestDao.observeRequests("listener-1").test {
            assertEquals(RequestStatus.PENDING, awaitItem().single().status)

            songRequestDao.upsertAll(listOf(stored.copy(status = RequestStatus.ACCEPTED, scheduledBroadcast = at(60))))

            val updated = awaitItem().single()
            assertEquals(RequestStatus.ACCEPTED, updated.status)
            assertEquals(at(60), updated.scheduledBroadcast)
            assertEquals("Bitte laut", updated.message)
        }
    }

    @Test
    fun aRequestIsFoundByItsIdempotencyKeyOnly() = runTest {
        songRequestDao.upsertAll(listOf(songRequestEntity(idempotencyKey = "key-1")))

        assertEquals("key-1", songRequestDao.findRequest("key-1")?.idempotencyKey)
        assertNull(songRequestDao.findRequest("key-2"))
    }
}
