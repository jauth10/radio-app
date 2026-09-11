package com.iu.radioapp.data.remote.s1playout

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FakePlayoutDataSourceTest {

    private val fixedNow = Instant.parse("2026-08-28T12:00:00Z")
    private val fake = FakePlayoutDataSource(clock = object : Clock {
        override fun now() = fixedNow
    })

    @Test
    fun `getCurrentTrack succeeds with the fixture track`() = runTest {
        val outcome = fake.getCurrentTrack()
        val track = (outcome as Outcome.Success).value
        assertEquals("trk-1", track?.trackId)
    }

    @Test
    fun `getCurrentTrack returns a null value when a talk segment is on air`() = runTest {
        fake.currentTrack = null
        val outcome = fake.getCurrentTrack()
        assertNull((outcome as Outcome.Success).value)
    }

    @Test
    fun `getHistory rejects a negative limit instead of throwing`() = runTest {
        val outcome = fake.getHistory(-1)
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `loginHost computes validUntil from the injected clock`() = runTest {
        val outcome = fake.loginHost("code", "device-1") as Outcome.Success
        assertEquals(fixedNow.plus(kotlin.time.Duration.parse("PT1H")), outcome.value.validUntil)
    }

    @Test
    fun `getCurrentTrack returns Connection failure when set`() = runTest {
        fake.nextFailure = Failure.Connection
        assertEquals(Failure.Connection, (fake.getCurrentTrack() as Outcome.Error).failure)
    }

    @Test
    fun `getCurrentTrack returns Server failure when set`() = runTest {
        fake.nextFailure = Failure.Server
        assertEquals(Failure.Server, (fake.getCurrentTrack() as Outcome.Error).failure)
    }

    @Test
    fun `getCurrentTrack returns Rejected failure when set`() = runTest {
        fake.nextFailure = Failure.Rejected(reason = "maintenance", retryable = true)
        val failure = (fake.getCurrentTrack() as Outcome.Error).failure as Failure.Rejected
        assertEquals("maintenance", failure.reason)
    }

    @Test
    fun `getCurrentTrack returns Unauthorized failure when set`() = runTest {
        fake.nextFailure = Failure.Unauthorized
        assertEquals(Failure.Unauthorized, (fake.getCurrentTrack() as Outcome.Error).failure)
    }

    @Test
    fun `failureRepeatCount fails that many calls in a row, then succeeds`() = runTest {
        fake.nextFailure = Failure.Server
        fake.failureRepeatCount = 2

        assertTrue(fake.getCurrentTrack() is Outcome.Error)
        assertTrue(fake.getCurrentTrack() is Outcome.Error)
        assertTrue(fake.getCurrentTrack() is Outcome.Success)
    }
}
