@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.OperationType
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
 * Instrumented tests for the outbox, in particular for its transactional
 * methods: queueing a song request, booking its delivery and booking its
 * rejection.
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

    /**
     * Review finding from PR #5: Room's @Update matches by primary key only. A
     * request whose row is gone updates zero rows and reports success, so
     * without the check the entry would stay DELIVERED forever - unreachable for
     * the worker, which only looks at OPEN entries.
     */
    @Test
    fun bookingDeliverySuccessRollsBackWhenNoRequestRowExists() = runTest {
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))
        // No song_request row for key-1 at all.

        var thrown: Throwable? = null
        try {
            outboxDao.bookDeliverySuccess(
                entryId = entryId,
                request = songRequestEntity(idempotencyKey = "key-1", requestId = "req-1"),
            )
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull("updating a missing request row must not count as success", thrown)
        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
    }

    /** An entry may only ever book the request it actually carries. */
    @Test
    fun bookingDeliverySuccessRejectsAMismatchedPair() = runTest {
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-1"))
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-2"))
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        var thrown: Throwable? = null
        try {
            outboxDao.bookDeliverySuccess(
                entryId = entryId,
                request = songRequestEntity(idempotencyKey = "key-2", requestId = "req-1"),
            )
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("the entry does not belong to that request", thrown)
        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
        assertNull(songRequestDao.findRequest("key-2")?.requestId)
    }

    @Test
    fun markingARatingDeliveredSetsTheStatus() = runTest {
        val entryId = outboxDao.insert(
            outboxEntity(idempotencyKey = "key-1", operation = OperationType.RATING)
        )

        outboxDao.markDelivered(entryId)

        assertEquals(DeliveryStatus.DELIVERED, outboxDao.findEntry(entryId)?.status)
    }

    @Test
    fun markingDeliveredRefusesASongRequestEntry() = runTest {
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-1"))
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        var thrown: Throwable? = null
        try {
            outboxDao.markDelivered(entryId)
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("a song request has to go through bookDeliverySuccess", thrown)
        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
    }

    @Test
    fun bookingDeliverySuccessRefusesARatingEntry() = runTest {
        val entryId = outboxDao.insert(
            outboxEntity(idempotencyKey = "key-1", operation = OperationType.RATING)
        )

        var thrown: Throwable? = null
        try {
            outboxDao.bookDeliverySuccess(
                entryId = entryId,
                request = songRequestEntity(idempotencyKey = "key-1", requestId = "req-1"),
            )
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("a rating has to go through markDelivered", thrown)
        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
    }

    @Test
    fun markingARatingRejectedIsTerminalAndKeepsTheReason() = runTest {
        val entryId = outboxDao.insert(
            outboxEntity(idempotencyKey = "key-1", operation = OperationType.RATING)
        )

        outboxDao.markRejected(entryId, reason = "already rated")

        val entry = outboxDao.findEntry(entryId)
        assertEquals(DeliveryStatus.REJECTED, entry?.status)
        assertEquals("already rated", entry?.rejectionReason)
        assertEquals(emptyList<String>(), outboxDao.openEntries().map { it.idempotencyKey })
    }

    @Test
    fun markingRejectedRefusesASongRequestEntry() = runTest {
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-1"))
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        var thrown: Throwable? = null
        try {
            outboxDao.markRejected(entryId, reason = "track not broadcastable")
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("a song request has to go through bookRejection", thrown)
        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
        assertNull(outboxDao.findEntry(entryId)?.rejectionReason)
    }

    @Test
    fun bookingARejectionSetsEntryAndRequestTogether() = runTest {
        songRequestDao.insert(songRequestEntity(idempotencyKey = "key-1"))
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        outboxDao.bookRejection(entryId, reason = "track not broadcastable")

        val entry = outboxDao.findEntry(entryId)
        assertEquals(DeliveryStatus.REJECTED, entry?.status)
        assertEquals("track not broadcastable", entry?.rejectionReason)
        val request = songRequestDao.findRequest("key-1")
        assertEquals(RequestStatus.REJECTED, request?.status)
        assertEquals("track not broadcastable", request?.rejectionReason)
    }

    @Test
    fun bookingARejectionRollsBackWhenNoRequestRowExists() = runTest {
        val entryId = outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))
        // No song_request row for key-1 at all.

        var thrown: Throwable? = null
        try {
            outboxDao.bookRejection(entryId, reason = "track not broadcastable")
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull("rejecting a missing request row must not count as success", thrown)
        val entry = outboxDao.findEntry(entryId)
        assertEquals(DeliveryStatus.OPEN, entry?.status)
        assertNull(entry?.rejectionReason)
    }

    @Test
    fun bookingARejectionRefusesARatingEntry() = runTest {
        val entryId = outboxDao.insert(
            outboxEntity(idempotencyKey = "key-1", operation = OperationType.RATING)
        )

        var thrown: Throwable? = null
        try {
            outboxDao.bookRejection(entryId, reason = "already rated")
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("a rating has to go through markRejected", thrown)
        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
    }

    @Test
    fun enqueueingASongRequestCreatesEntryAndRequestTogether() = runTest {
        val entryId = outboxDao.enqueueSongRequest(
            entry = outboxEntity(idempotencyKey = "key-1"),
            request = songRequestEntity(idempotencyKey = "key-1"),
        )

        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(entryId)?.status)
        assertEquals(RequestStatus.PENDING, songRequestDao.findRequest("key-1")?.status)
    }

    @Test
    fun enqueueingASongRequestRollsBackWhenTheEntryInsertFails() = runTest {
        outboxDao.insert(outboxEntity(idempotencyKey = "key-1"))

        var thrown: Throwable? = null
        try {
            outboxDao.enqueueSongRequest(
                entry = outboxEntity(idempotencyKey = "key-1"),
                request = songRequestEntity(idempotencyKey = "key-1"),
            )
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }

        assertNotNull("the second entry was expected to violate the unique index", thrown)
        assertNull(songRequestDao.findRequest("key-1"))
    }

    @Test
    fun enqueueingASongRequestRejectsAMismatchedPair() = runTest {
        var thrown: Throwable? = null
        try {
            outboxDao.enqueueSongRequest(
                entry = outboxEntity(idempotencyKey = "key-1"),
                request = songRequestEntity(idempotencyKey = "key-2"),
            )
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("the entry does not belong to that request", thrown)
        assertNull(outboxDao.findByIdempotencyKey("key-1"))
        assertNull(songRequestDao.findRequest("key-2"))
    }

    @Test
    fun reopeningAFailedEntryKeepsTheAttemptCounter() = runTest {
        val entryId = outboxDao.insert(
            outboxEntity(
                idempotencyKey = "key-1",
                attempts = 5,
                lastAttemptAt = at(3),
                status = DeliveryStatus.FAILED,
            )
        )

        outboxDao.reopen(entryId)

        val entry = outboxDao.findEntry(entryId)
        assertEquals(DeliveryStatus.OPEN, entry?.status)
        assertEquals(5, entry?.attempts)
        assertEquals(at(3), entry?.lastAttemptAt)
    }

    @Test
    fun reopeningRefusesARejectedEntry() = runTest {
        val entryId = outboxDao.insert(
            outboxEntity(idempotencyKey = "key-1", operation = OperationType.RATING)
        )
        outboxDao.markRejected(entryId, reason = "already rated")

        var thrown: Throwable? = null
        try {
            outboxDao.reopen(entryId)
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        assertNotNull("only FAILED may be reopened", thrown)
        assertEquals(DeliveryStatus.REJECTED, outboxDao.findEntry(entryId)?.status)
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
