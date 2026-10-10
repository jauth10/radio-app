package com.iu.radioapp.ui

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import com.iu.radioapp.domain.Host
import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.Playback
import com.iu.radioapp.domain.RatingContext
import com.iu.radioapp.domain.Show
import com.iu.radioapp.domain.Track
import kotlin.time.Instant

fun str(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

val T0: Instant = Instant.parse("2026-10-10T10:00:00Z")

fun track(id: String = "trk-1", title: String = "Sample Song", broadcastable: Boolean? = true) = Track(
    trackId = id,
    artist = "The Fake Band",
    title = title,
    album = "Fake Album",
    coverUrl = null,
    durationSeconds = 210,
    broadcastable = broadcastable,
)

val host = Host("host-1", "Alex Host")
val show = Show("show-1", name = null, startsAt = null, endsAt = null, host = host)

fun playback(track: Track = track(), startedAt: Instant = T0) =
    Playback(playbackId = "${track.trackId}@$startedAt", startedAt = startedAt, track = track, showId = "show-1")

fun nowPlaying(withHost: Boolean = true) =
    NowPlaying(playback(), show.copy(host = if (withHost) host else null))

fun ratingContext(host: Host? = com.iu.radioapp.ui.host, isStale: Boolean = false) =
    RatingContext(show = show, host = host, isStale = isStale)
