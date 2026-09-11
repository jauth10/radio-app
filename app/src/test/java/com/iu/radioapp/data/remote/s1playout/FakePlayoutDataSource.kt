package com.iu.radioapp.data.remote.s1playout

import com.iu.radioapp.data.remote.FailureSwitch
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
 * Set [nextFailure] (and optionally [failureRepeatCount]) before a call to make
 * the next one, or the next N, fail with that class.
 *
 * [currentTrack] is a settable `var`, not fixed sample data returned as-is: a
 * test can null it out to simulate a talk segment (204), or swap in a track
 * with no host to exercise the unhosted-show case.
 *
 * [clock] defaults to [Clock.System] but can be replaced with a fixed clock in
 * a test, since real time would otherwise make [loginHost]'s validUntil and
 * comparisons against it non-reproducible.
 */
@OptIn(ExperimentalTime::class)
class FakePlayoutDataSource(private val clock: Clock = Clock.System) : PlayoutDataSource {

    private val failures = FailureSwitch()

    var nextFailure: Failure?
        get() = failures.nextFailure
        set(value) { failures.nextFailure = value }

    var failureRepeatCount: Int
        get() = failures.times
        set(value) { failures.times = value }

    var currentTrack: CurrentTrackDto? = CurrentTrackDto(
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

    override suspend fun getCurrentTrack(): Outcome<CurrentTrackDto?> {
        failures.consume()?.let { return Outcome.Error(it) }
        return Outcome.Success(currentTrack)
    }

    override suspend fun getHistory(limit: Int): Outcome<List<HistoryEntryDto>> {
        failures.consume()?.let { return Outcome.Error(it) }
        if (limit < 0) {
            return Outcome.Error(Failure.Rejected(reason = "limit must not be negative", retryable = false))
        }
        return Outcome.Success(history.take(limit))
    }

    override suspend fun loginHost(hostCode: String, deviceId: String): Outcome<HostLoginResponse> {
        failures.consume()?.let { return Outcome.Error(it) }
        return Outcome.Success(
            HostLoginResponse(
                sessionToken = "fake-session-$deviceId",
                validUntil = clock.now() + 1.hours,
                hostId = "host-1",
                hostName = "Alex Host",
            )
        )
    }
}
