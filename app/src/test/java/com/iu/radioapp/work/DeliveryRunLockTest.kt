package com.iu.radioapp.work

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.local.OutboxEntity
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OperationType
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.repository.MutableClock
import com.iu.radioapp.repository.deliverOpenEntries
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DeliveryRunLockTest {

    private val store = InMemoryLocalStore()
    private val outboxDao = FakeOutboxDao(store)
    private val clock = MutableClock()
    private val lock = DeliveryRunLock()
    private val sent = mutableListOf<String>()

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

    private suspend fun entry(key: String) = checkNotNull(outboxDao.findByIdempotencyKey(key))

    private suspend fun run(answer: Outcome<Unit>) = lock.exclusive {
        outboxDao.deliverOpenEntries(
            operation = OperationType.RATING,
            clock = clock,
            send = { entry ->
                sent += entry.idempotencyKey
                delay(SEND_TIME)
                answer
            },
            onDelivered = { entry, _ -> outboxDao.markDelivered(entry.id) },
            onRejected = { entry, reason -> outboxDao.markRejected(entry.id, reason) },
        )
    }

    @Test
    fun `two runs at the same time send every entry once`() = runTest {
        enqueue("key-1")
        enqueue("key-2")

        launch { run(Outcome.Success(Unit)) }
        launch { run(Outcome.Success(Unit)) }
        advanceTimeBy(10.seconds)

        assertEquals(listOf("key-1", "key-2"), sent)
        assertEquals(DeliveryStatus.DELIVERED, entry("key-1").status)
        assertEquals(DeliveryStatus.DELIVERED, entry("key-2").status)
    }

    @Test
    fun `two failing runs at the same time count every attempt once`() = runTest {
        enqueue("key-1")

        launch { run(Outcome.Error(Failure.Server)) }
        launch { run(Outcome.Error(Failure.Server)) }
        advanceTimeBy(10.seconds)

        assertEquals(listOf("key-1", "key-1"), sent)
        assertEquals(2, entry("key-1").attempts)
        assertEquals(DeliveryStatus.OPEN, entry("key-1").status)
    }

    @Test
    fun `a run cancelled while sending books nothing and lets the next run send again`() = runTest {
        enqueue("key-1")

        val first = launch { run(Outcome.Error(Failure.Server)) }
        runCurrent()
        val second = launch { run(Outcome.Success(Unit)) }
        advanceTimeBy(SEND_TIME / 2)
        first.cancel()
        runCurrent()

        assertEquals(0, entry("key-1").attempts)
        assertEquals(DeliveryStatus.OPEN, entry("key-1").status)

        second.join()
        assertEquals(listOf("key-1", "key-1"), sent)
        assertEquals(0, entry("key-1").attempts)
        assertEquals(DeliveryStatus.DELIVERED, entry("key-1").status)
    }

    private companion object {
        val SEND_TIME = 1.seconds
    }
}
