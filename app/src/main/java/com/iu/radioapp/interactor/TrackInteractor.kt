package com.iu.radioapp.interactor

import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.NowPlayingState
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Playback
import com.iu.radioapp.domain.ReadResult
import com.iu.radioapp.domain.Track
import com.iu.radioapp.domain.completedWith
import com.iu.radioapp.domain.map
import com.iu.radioapp.repository.ArchiveRepository
import com.iu.radioapp.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class TrackInteractor @Inject constructor(
    private val tracks: TrackRepository,
    private val archive: ArchiveRepository,
    private val clock: Clock,
) {

    suspend fun getNowPlaying(): Outcome<NowPlayingState> =
        tracks.getCurrentPlayback().map { it.toState() }

    suspend fun getTrackDetail(track: Track): Outcome<Track> =
        archive.getTrack(track.trackId).map { track.completedWith(it) }

    fun observeHistory(): Flow<List<Playback>> = tracks.observeHistory()

    suspend fun refreshHistory(): Outcome<Unit> = tracks.refreshHistory(HISTORY_LIMIT)

    private fun ReadResult<NowPlaying?>.toState(): NowPlayingState = when (this) {
        is ReadResult.Live -> value?.let { NowPlayingState.Live(it) } ?: NowPlayingState.TalkSegment
        is ReadResult.Cached -> NowPlayingState.Cached(
            nowPlaying = checkNotNull(value) { "a cached playback always carries a track" },
            fetchedAt = fetchedAt,
            isStale = clock.now() - fetchedAt > STALE_AFTER,
            cause = cause,
        )
    }

    companion object {
        val STALE_AFTER: Duration = 5.minutes

        // Same cap as PlaybackHistoryDao.
        const val HISTORY_LIMIT = 50
    }
}
