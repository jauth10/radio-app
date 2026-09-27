package com.iu.radioapp.repository

import com.iu.radioapp.data.remote.s2archive.FakeArchiveDataSource
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRepositoryTest {

    private val archive = FakeArchiveDataSource()
    private val repository = ArchiveRepository(archive)

    @Test
    fun `getTrack maps the archive record and leaves the duration unknown`() = runTest {
        val track = (repository.getTrack("trk-3") as Outcome.Success).value

        assertEquals("trk-3", track.trackId)
        assertEquals(false, track.broadcastable)
        assertNull(track.durationSeconds)
    }

    @Test
    fun `searchTracks maps every hit`() = runTest {
        val expected = (archive.searchTracks("a", 10) as Outcome.Success).value.map { it.trackId }

        val tracks = (repository.searchTracks("a", 10) as Outcome.Success).value

        assertEquals(expected, tracks.map { it.trackId })
    }

    @Test
    fun `connection failure is passed on`() = runTest {
        archive.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), repository.searchTracks("a", 10))
    }

    @Test
    fun `server failure is passed on`() = runTest {
        archive.nextFailure = Failure.Server

        assertEquals(Outcome.Error(Failure.Server), repository.getTrack("trk-1"))
    }

    @Test
    fun `unknown track is a rejection`() = runTest {
        val outcome = repository.getTrack("does-not-exist")

        assertTrue((outcome as Outcome.Error).failure is Failure.Rejected)
    }
}
