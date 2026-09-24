@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One airing that has already happened: this track, started at this moment.
 *
 * [playbackId] is formed on the device from track id and start time, because no
 * S1 endpoint hands out an id for an airing. Using it as the
 * primary key makes re-reading the same history idempotent: polling the station
 * twice updates the rows instead of duplicating them.
 *
 * The track fields are carried along instead of pointing into track_cache. The
 * history endpoint returns them inline, and a foreign key would mean the history
 * breaks apart as soon as the cache is cleaned up - a cache is by definition
 * allowed to forget, the history is not.
 *
 * [showId] is nullable because the history endpoint returns no show reference.
 *
 * The newest row of this table doubles as "what is on air right now"; there is
 * no separate flag for it.
 */
@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["started_at"])],
)
data class PlaybackHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "playback_id")
    val playbackId: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String?,
    @ColumnInfo(name = "started_at")
    val startedAt: Instant,
    @ColumnInfo(name = "show_id")
    val showId: String?,
)
