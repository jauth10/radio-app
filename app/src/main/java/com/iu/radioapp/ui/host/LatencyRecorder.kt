package com.iu.radioapp.ui.host

import androidx.compose.runtime.staticCompositionLocalOf
import com.iu.radioapp.domain.ReceivedRating

/** Latency measurement for AK 5.10/5.11 (RAD-18); only the debug build records, release binds [NoLatencyRecorder]. */
interface LatencyRecorder {
    fun onListed(ratings: List<ReceivedRating>)
    fun onDisplayed(received: ReceivedRating)
}

object NoLatencyRecorder : LatencyRecorder {
    override fun onListed(ratings: List<ReceivedRating>) = Unit
    override fun onDisplayed(received: ReceivedRating) = Unit
}

val LocalLatencyRecorder = staticCompositionLocalOf<LatencyRecorder> { NoLatencyRecorder }
