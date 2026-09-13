@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.RequestStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 * Instrumented tests for the outbox, in particular for the transactional
 * booking of a successful delivery.
 */
@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {

    private lateinit var database: RadioDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var songRequestDao: SongRequestDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        outboxDao = database.outboxDao()
        songRequestDao = database.songRequestDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bookingDeliverySuccessSetsStatusAndRequestTogether() = runTest {
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-1"))
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        outboxDao.bookDeliverySuccess(
            entryId = entryId,
            request = songRequestEntity(
                idempotencyKey = "key-1",
                requestId = "req-1",
                status = RequestStatus.PENDING,
            ),
        )

        assertEquals(DeliveryStatus.DELIVERED, outboxDao.findEntry(entryId)?.status)
        assertEquals("req-1", songRequestDao.findRequest("key-1")?.requestId)
    }

    /**
     * The point of the transaction: if step two fails, step one must not survive
     * either. Without the rollback the queue entry would count as delivered
     * while the request still has no station id - and nothing would ever fix
     * that, because the delivery worker only looks at OPEN entries.
     */
    @Test
    fun bookingDeliverySuccessRollsBackWhenTheRequestUpdateFails() = runTest {
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-1"))
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-2", requestId = "req-1"))
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        var thrown: Throwable? = null
        try {
            outboxDao.bookDeliverySuccess(
                entryId = entryId,
                request = songRequestEntity(idempotencyKey = "key-1", requestId = "req-1"),
            )
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull("step two was expected to violate the unique index", thrown)

        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
        assertNull(songRequestDao.findRequest("key-1")?.requestId)
    }

    @Test
    fun recordingAnAttemptKeepsTheIdempotencyKeyAndCountsUp() = runTest {
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        outboxDao.recordAttempt(
            entryId = entryId,
            attempts = 5,
            attemptedAt = at(3),
            status = DeliveryStatus.FAILED,
        )

        val entry = outboxDao.findEntry(entryId)
        assertEquals(5, entry?.attempts)
        assertEquals(DeliveryStatus.FAILED, entry?.status)
        assertEquals("key-1", entry?.idempotencyKey)
    }

    @Test
    fun onlyOpenEntriesAreHandedToTheDeliveryWorker() = runTest {
        outboxDao.insert(outboxEntity(idempotencyKey = "key-open"))
        outboxDao.insert(
            outboxEntity(idempotencyKey = "key-done", status = DeliveryStatus.DELIVERED)
        )

        val open = outboxDao.openEntries()

        assertEquals(listOf("key-open"), open.map { it.idempotencyKey })
    }
}
