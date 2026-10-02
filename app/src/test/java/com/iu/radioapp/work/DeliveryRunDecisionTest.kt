package com.iu.radioapp.work

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryRunDecisionTest {

    private val delivered: Result<Outcome<Unit>> = Result.success(Outcome.Success(Unit))

    private fun failed(failure: Failure): Result<Outcome<Unit>> = Result.success(Outcome.Error(failure))

    @Test
    fun `success of both repositories is done`() {
        assertEquals(NextRun.DONE, nextRunAfter(listOf(delivered, delivered)))
    }

    @Test
    fun `server failure asks for a retry`() {
        assertEquals(NextRun.RETRY, nextRunAfter(listOf(failed(Failure.Server), delivered)))
    }

    @Test
    fun `connection failure asks for a retry`() {
        assertEquals(NextRun.RETRY, nextRunAfter(listOf(delivered, failed(Failure.Connection))))
    }

    @Test
    fun `unauthorized on the listener path asks for a retry`() {
        assertEquals(NextRun.RETRY, nextRunAfter(listOf(failed(Failure.Unauthorized), delivered)))
    }

    @Test
    fun `local exception asks for a retry`() {
        val crashed: Result<Outcome<Unit>> = Result.failure(IllegalStateException("broken"))

        assertEquals(NextRun.RETRY, nextRunAfter(listOf(crashed, delivered)))
    }

    @Test
    fun `rejection is done even when marked retryable`() {
        assertEquals(NextRun.DONE, nextRunAfter(listOf(failed(Failure.Rejected("limit", retryable = true)), delivered)))
    }

    @Test
    fun `nothing to deliver is done`() {
        assertEquals(NextRun.DONE, nextRunAfter(emptyList()))
    }

    @Test
    fun `captureDelivery wraps an exception`() = runTest {
        val broken = IllegalStateException("broken")

        val result = captureDelivery { throw broken }

        assertSame(broken, result.exceptionOrNull())
    }

    @Test
    fun `captureDelivery passes the outcome through`() = runTest {
        assertEquals(Outcome.Error(Failure.Server), captureDelivery { Outcome.Error(Failure.Server) }.getOrNull())
    }

    @Test
    fun `captureDelivery lets cancellation through`() = runTest {
        val thrown = runCatching { captureDelivery { throw CancellationException("stopped") } }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }
}
