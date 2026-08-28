package com.iu.radioapp.data.remote.s2archive

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s2archive.TrackDto

/**
 * In-memory fake of [ArchiveDataSource] with a fixed catalogue.
 *
 * [getTrackDetail] answers an unknown id with [Failure.Rejected] on its own,
 * the way the real archive would answer with 404 - on top of that, [nextFailure]
 * still lets a test force any of the four classes for the next call.
 */
class FakeArchiveDataSource : ArchiveDataSource {

    var nextFailure: Failure? = null

    private val tracks = listOf(
        TrackDto("trk-1", "The Fake Band", "Sample Song", "Fake Album", null, broadcastable = true),
        TrackDto("trk-2", "Second Artist", "Second Song", "Some Album", null, broadcastable = true),
        TrackDto("trk-3", "Third Artist", "Third Song", null, null, broadcastable = false),
    )

    private fun consumeFailure(): Failure? = nextFailure.also { nextFailure = null }

    override suspend fun searchTracks(query: String, limit: Int): Outcome<List<TrackDto>> {
        consumeFailure()?.let { return Outcome.Error(it) }
        val matches = tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        return Outcome.Success(matches.take(limit))
    }

    override suspend fun getTrackDetail(trackId: String): Outcome<TrackDto> {
        consumeFailure()?.let { return Outcome.Error(it) }
        val track = tracks.find { it.trackId == trackId }
            ?: return Outcome.Error(Failure.Rejected(reason = "unknown trackId: $trackId", retryable = false))
        return Outcome.Success(track)
    }
}
