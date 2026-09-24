@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Cached master data of a track, independent of any airing.
 *
 * This is the Room twin of the domain type Track, not the type itself: the
 * domain stays free of annotations, the mapping happens in TrackCacheMapper.
 *
 * [fetchedAt] is the moment this row was written from a station response. It is
 * the only reason the table can answer "how old is this?", which is what the
 * five minute staleness rule needs. The rule itself is NOT evaluated here -
 * that is the TrackInteractor's job; this layer never reads the clock.
 *
 * [durationSeconds] and [broadcastable] are nullable for the same reason as in
 * the domain type: S1 knows the duration, S2 knows whether a track may be
 * requested, and no endpoint supplies both.
 */
@Entity(tableName = "track_cache")
data class TrackCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String?,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int?,
    val broadcastable: Boolean?,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Instant,
)
