package com.iu.radioapp.data.remote.s1playout

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import contract.s1playout.HostLoginResponse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * In-memory fake of [PlayoutDataSource] with fixed sample data.
 *
 * Set [nextFailure] before a call to make exactly that call fail with the
 * given class; it is consumed and reset to null right after being read.
 */
@OptIn(ExperimentalTime::class)
class FakePlayoutDataSource : PlayoutDataSource {

    var nextFailure: Failure? = null

    private val currentTrack = CurrentTrackDto(
        trackId = "trk-1",
        artist = "The Fake Band",
        title = "Sample Song",
        album = "Fake Album",
        coverUrl = null,
        durationSeconds = 210,
        startedAt = Instant.parse("2026-08-28T10:00:00Z"),
        showId = "show-1",
        hostId = "host-1",
        hostName = "Alex Host",
    )

    private val history = listOf(
        HistoryEntryDto("trk-1", "The Fake Band", "Sample Song", "Fake Album", Instant.parse("2026-08-28T10:00:00Z")),
        HistoryEntryDto("trk-2", "Second Artist", "Second Song", "Some Album", Instant.parse("2026-08-28T09:50:00Z")),
        HistoryEntryDto("trk-3", "Third Artist", "Third Song", null, Instant.parse("2026-08-28T09:40:00Z")),
    )

    private fun consumeFailure(): Failure? = nextFailure.also { nextFailure = null }

    override suspend fun getCurrentTrack(): Outcome<CurrentTrackDto> {
        consumeFailure()?.let { return Outcome.Error(it) }
        return Outcome.Success(currentTrack)
    }

    override suspend fun getHistory(limit: Int): Outcome<List<HistoryEntryDto>> {
        consumeFailure()?.let { return Outcome.Error(it) }
        return Outcome.Success(history.take(limit))
    }

    override suspend fun loginHost(hostCode: String, deviceId: String): Outcome<HostLoginResponse> {
        consumeFailure()?.let { return Outcome.Error(it) }
        return Outcome.Success(
            HostLoginResponse(
                sessionToken = "fake-session-$deviceId",
                validUntil = Clock.System.now() + 1.hours,
                hostId = "host-1",
                hostName = "Alex Host",
            )
        )
    }
}
