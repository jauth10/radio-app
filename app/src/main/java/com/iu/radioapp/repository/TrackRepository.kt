package com.iu.radioapp.repository

import com.iu.radioapp.data.local.PlaybackHistoryDao
import com.iu.radioapp.data.local.TrackCacheDao
import com.iu.radioapp.data.local.toCacheEntity
import com.iu.radioapp.data.local.toDomain
import com.iu.radioapp.data.local.toHistoryEntity
import com.iu.radioapp.data.remote.s1playout.PlayoutDataSource
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Playback
import com.iu.radioapp.domain.ReadResult
import com.iu.radioapp.domain.Show
import com.iu.radioapp.repository.mapping.toNowPlaying
import com.iu.radioapp.repository.mapping.toPlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Clock

class TrackRepository @Inject constructor(
    private val playout: PlayoutDataSource,
    private val trackCacheDao: TrackCacheDao,
    private val historyDao: PlaybackHistoryDao,
    private val clock: Clock,
) {

    suspend fun getCurrentPlayback(): Outcome<ReadResult<NowPlaying?>> =
        when (val outcome = playout.getCurrentTrack()) {
            is Outcome.Success -> {
                val fetchedAt = clock.now()
                val nowPlaying = outcome.value?.toNowPlaying()
                // null is a talk segment (204): nothing is stored, the cache keeps the last track.
                if (nowPlaying != null) {
                    trackCacheDao.upsert(nowPlaying.playback.track.toCacheEntity(fetchedAt))
                    historyDao.appendAndTrim(nowPlaying.playback.toHistoryEntity())
                }
                Outcome.Success(ReadResult.Live(nowPlaying, fetchedAt))
            }
            is Outcome.Error -> cachedPlayback(outcome.failure)
        }

    suspend fun refreshHistory(limit: Int): Outcome<Unit> =
        when (val outcome = playout.getHistory(limit)) {
            is Outcome.Success -> {
                // S1 history carries no showId; a known playback must not lose the one it has.
                val known = historyDao.observeHistory().first().map { it.playbackId }.toSet()
                outcome.value
                    .map { it.toPlayback().toHistoryEntity() }
                    .filter { it.playbackId !in known }
                    .forEach { historyDao.appendAndTrim(it) }
                Outcome.Success(Unit)
            }
            is Outcome.Error -> outcome
        }

    fun observeHistory(): Flow<List<Playback>> =
        historyDao.observeHistory().map { rows -> rows.map { it.toDomain() } }

    private suspend fun cachedPlayback(cause: Failure): Outcome<ReadResult<NowPlaying?>> {
        if (cause !is Failure.Connection && cause !is Failure.Server) return Outcome.Error(cause)
        val latest = historyDao.observeLatest().first() ?: return Outcome.Error(cause)
        val cachedTrack = trackCacheDao.findTrack(latest.trackId) ?: return Outcome.Error(cause)
        val nowPlaying = NowPlaying(
            playback = latest.toDomain().copy(track = cachedTrack.toDomain()),
            show = latest.showId?.let { Show(showId = it, name = null, startsAt = null, endsAt = null, host = null) },
        )
        return Outcome.Success(ReadResult.Cached(nowPlaying, cachedTrack.fetchedAt, cause))
    }
}
