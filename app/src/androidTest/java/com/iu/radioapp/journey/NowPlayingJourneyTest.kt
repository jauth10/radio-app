package com.iu.radioapp.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.common.StateTags
import com.iu.radioapp.ui.nowplaying.NowPlayingTags
import com.iu.radioapp.ui.str
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

@HiltAndroidTest
class NowPlayingJourneyTest : JourneyTest() {

    @Test
    fun us1_start_shows_the_running_track_its_host_and_the_history() {
        launchApp()

        waitForText("Sample Song")
        composeRule.onNodeWithText(str(R.string.now_playing_host, "Alex Host")).assertExists()
        waitForText("Second Song")
        composeRule.onNodeWithText("Third Song").assertExists()
    }

    @Test
    fun us1_error_a_station_fault_is_an_error_not_offline() {
        playout.nextFailure = Failure.Server
        playout.failureRepeatCount = ALWAYS
        launchApp()

        waitForText(str(R.string.error_title))
        waitForText(str(R.string.failure_server))
        assertTrue(hasNoText(str(R.string.offline_title)))
    }

    @Test
    fun us1_offline_keeps_the_last_track_and_marks_it_stale_after_five_minutes() {
        launchApp()
        waitForText("Sample Song")

        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = ALWAYS
        waitForText(str(R.string.now_playing_last_known), timeoutMs = NEXT_POLL_MS)
        currentTrack("Sample Song").assertExists()
        assertTrue(hasNoText(str(R.string.stale_marker)))

        clock.advanceBy(6.minutes)
        waitForTag(StateTags.STALE_MARKER, timeoutMs = NEXT_POLL_MS)
        currentTrack("Sample Song").assertExists()
    }

    @Test
    fun us1_offline_without_a_stored_track_offers_a_reload_that_recovers() {
        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = ALWAYS
        launchApp()

        waitForText(str(R.string.offline_no_data))
        playout.nextFailure = null
        composeRule.onNodeWithText(str(R.string.action_reload)).performClick()

        waitForText("Sample Song")
        composeRule.onNodeWithTag(StateTags.OFFLINE_BANNER).assertDoesNotExist()
    }

    private fun currentTrack(title: String) =
        composeRule.onNode(hasText(title) and hasAnyAncestor(hasTestTag(NowPlayingTags.CURRENT)), useUnmergedTree = true)
}
