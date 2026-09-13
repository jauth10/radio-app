package com.iu.radioapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's local database: track cache, playback history, outbox, own requests.
 *
 * Declared internal, and no layer above data/local is meant to name this type.
 * What leaves the Hilt graph are the four DAOs, never the database itself -
 * otherwise any caller could open its own transaction and the rule "transactions
 * live in @Transaction DAO methods" would quietly stop holding. In a single
 * module app internal cannot enforce that on its own; it keeps the
 * type out of contract and stub-server, the rest is a review rule.
 *
 * Schema export is on. The generated schemas/<class>/1.json is committed, so a
 * later migration can be diffed against a recorded schema instead of a guess.
 */
@Database(
    entities = [
        TrackCacheEntity::class,
        PlaybackHistoryEntity::class,
        OutboxEntity::class,
        SongRequestEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class RadioDatabase : RoomDatabase() {

    abstract fun trackCacheDao(): TrackCacheDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    abstract fun outboxDao(): OutboxDao

    abstract fun songRequestDao(): SongRequestDao

    companion object {
        const val NAME = "radio.db"
    }
}
