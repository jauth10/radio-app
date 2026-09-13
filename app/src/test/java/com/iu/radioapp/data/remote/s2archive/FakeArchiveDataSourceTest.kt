package com.iu.radioapp.data.remote.s2archive

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeArchiveDataSourceTest {

    private val fake = FakeArchiveDataSource()

    @Test
    fun `searchTracks finds matches by title or artist, case-insensitive`() = runTest {
        val outcome = fake.searchTracks("sample", limit = 10) as Outcome.Success
        assertEquals(1, outcome.value.size)
        assertEquals("trk-1", outcome.value.first().trackId)
    }

    @Test
    fun `searchTracks rejects a negative limit instead of throwing`() = runTest {
        val outcome = fake.searchTracks("a", limit = -1)
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `getTrackDetail succeeds for a known id`() = runTest {
        val outcome = fake.getTrackDetail("trk-2") as Outcome.Success
        assertEquals("Second Song", outcome.value.title)
    }

    @Test
    fun `getTrackDetail rejects an unknown id`() = runTest {
        val outcome = fake.getTrackDetail("does-not-exist")
        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }

    @Test
    fun `searchTracks returns Connection failure when set`() = runTest {
        fake.nextFailure = Failure.Connection
        assertEquals(Failure.Connection, (fake.searchTracks("a", 10) as Outcome.Error).failure)
    }

    @Test
    fun `searchTracks returns Server failure when set`() = runTest {
        fake.nextFailure = Failure.Server
        assertEquals(Failure.Server, (fake.searchTracks("a", 10) as Outcome.Error).failure)
    }

    @Test
    fun `searchTracks returns Unauthorized failure when set`() = runTest {
        fake.nextFailure = Failure.Unauthorized
        assertEquals(Failure.Unauthorized, (fake.searchTracks("a", 10) as Outcome.Error).failure)
    }

    @Test
    fun `failureRepeatCount fails that many calls in a row, then succeeds`() = runTest {
        fake.nextFailure = Failure.Server
        fake.failureRepeatCount = 2

        assertTrue(fake.searchTracks("a", 10) is Outcome.Error)
        assertTrue(fake.searchTracks("a", 10) is Outcome.Error)
        assertTrue(fake.searchTracks("a", 10) is Outcome.Success)
    }
}
