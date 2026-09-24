package com.iu.radioapp.repository

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryPolicyTest {

    private val store = InMemoryLocalStore()
    private val outboxDao = FakeOutboxDao(store)
    private val clock = MutableClock()

    private suspend fun enqueue(key: String) = outboxDao.insert(
        OutboxEntity(
            idempotencyKey = key,
            operation = OperationType.RATING,
            payload = "{}",
            attempts = 0,
            lastAttemptAt = null,
            status = DeliveryStatus.OPEN,
            rejectionReason = null,
        )
    )

    private suspend fun entry(key: String) = outboxDao.findByIdempotencyKey(key)

    private suspend fun deliver(answers: Map<String, Outcome<Unit>>): Outcome<Unit> =
        outboxDao.deliverOpenEntries(
            operation = OperationType.RATING,
            clock = clock,
            send = { entry -> answers.getValue(entry.idempotencyKey) },
            onDelivered = { entry, _ -> outboxDao.markDelivered(entry.id) },
            onRejected = { entry, reason -> outboxDao.markRejected(entry.id, reason) },
        )

    @Test
    fun `connection failure after a server failure still stops the run`() = runTest {
        enqueue("key-1")
        enqueue("key-2")
        enqueue("key-3")

        val result = deliver(
            mapOf(
                "key-1" to Outcome.Error(Failure.Server),
                "key-2" to Outcome.Error(Failure.Connection),
                "key-3" to Outcome.Success(Unit),
            )
        )

        assertEquals(Outcome.Error(Failure.Server), result)
        assertEquals(1, entry("key-1")?.attempts)
        assertEquals(1, entry("key-2")?.attempts)
        assertEquals(0, entry("key-3")?.attempts)
        assertEquals(DeliveryStatus.OPEN, entry("key-3")?.status)
    }

    @Test
    fun `server failure does not stop the run`() = runTest {
        enqueue("key-1")
        enqueue("key-2")

        val result = deliver(
            mapOf(
                "key-1" to Outcome.Error(Failure.Server),
                "key-2" to Outcome.Success(Unit),
            )
        )

        assertEquals(Outcome.Error(Failure.Server), result)
        assertEquals(DeliveryStatus.OPEN, entry("key-1")?.status)
        assertEquals(DeliveryStatus.DELIVERED, entry("key-2")?.status)
    }

    @Test
    fun `rejection is booked and not reported as a technical failure`() = runTest {
        enqueue("key-1")
        enqueue("key-2")

        val result = deliver(
            mapOf(
                "key-1" to Outcome.Error(Failure.Rejected(reason = "already rated", retryable = true)),
                "key-2" to Outcome.Success(Unit),
            )
        )

        assertEquals(Outcome.Success(Unit), result)
        assertEquals(DeliveryStatus.REJECTED, entry("key-1")?.status)
        assertEquals("already rated", entry("key-1")?.rejectionReason)
        assertEquals(0, entry("key-1")?.attempts)
        assertEquals(DeliveryStatus.DELIVERED, entry("key-2")?.status)
    }

    @Test
    fun `entries of another operation are left untouched`() = runTest {
        enqueue("key-1")
        val otherId = outboxDao.insert(
            OutboxEntity(
                idempotencyKey = "key-s",
                operation = OperationType.SONG_REQUEST,
                payload = "{}",
                attempts = 0,
                lastAttemptAt = null,
                status = DeliveryStatus.OPEN,
                rejectionReason = null,
            )
        )

        deliver(mapOf("key-1" to Outcome.Success(Unit)))

        assertEquals(DeliveryStatus.OPEN, outboxDao.findEntry(otherId)?.status)
    }
}
