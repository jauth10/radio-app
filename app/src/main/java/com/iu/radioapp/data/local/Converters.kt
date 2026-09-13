@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.data.local

import androidx.room.TypeConverter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class Converters {

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(epochMillis: Long?): Instant? =
        epochMillis?.let(Instant::fromEpochMilliseconds)
}
