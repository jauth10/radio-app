package com.iu.radioapp.journey

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.iu.radioapp.R
import com.iu.radioapp.data.remote.s4feedback.ConnectionEvent
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.host.HostFeedTags
import com.iu.radioapp.ui.host.HostLoginTags
import com.iu.radioapp.ui.navigation.NavTags
import com.iu.radioapp.ui.navigation.TopLevel
import com.iu.radioapp.ui.str
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.RatingTarget
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class HostFeedJourneyTest : JourneyTest() {

    @Test
    fun us5_after_login_a_new_rating_appears_without_reloading() {
        launchApp()
        logIn("ALEX-2026")

        composeRule.waitUntil(TIMEOUT_MS) { ratingEvents.lastRequestedToken != null }
        composeRule.waitForIdle()
        ratingEvents.emit(ConnectionEvent.Connected)
        waitForText(str(R.string.host_connection_connected))

        val rating = RatingEventDto("rat-live", RatingTarget.PLAYLIST, 5, "Super Sendung", clock.now(), "Hörerin B")
        ratingEvents.emit(ConnectionEvent.MessageReceived(RatingEventMessage(EventType.NEW_RATING, rating.serverReceivedAt, rating)))

        waitForTag(HostFeedTags.rating("rat-live"))
        waitForText("Super Sendung")
        waitForText(str(R.string.host_rating_from, "Hörerin B"))
    }

    @Test
    fun us5_error_an_invalid_code_unlocks_nothing_and_leaves_the_listener_side_working() {
        launchApp()
        openHostEntry()
        playout.nextFailure = Failure.Unauthorized
        playout.failureRepeatCount = ALWAYS
        enterCode("FALSCH-1")

        waitForText(str(R.string.host_login_invalid))
        playout.nextFailure = null
        openArea(TopLevel.PROGRAM)
        waitForText("Sample Song")
    }

    @Test
    fun us5_offline_the_login_names_the_missing_connection() {
        launchApp()
        openHostEntry()
        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = ALWAYS
        enterCode("ALEX-2026")

        waitForText(str(R.string.host_login_offline))
    }

    private fun logIn(code: String) {
        openHostEntry()
        enterCode(code)
    }

    private fun openHostEntry() {
        waitForText("Sample Song")
        composeRule.onNodeWithTag(NavTags.HOST_ENTRY).performClick()
        waitForTag(HostLoginTags.CODE)
    }

    private fun enterCode(code: String) {
        composeRule.onNodeWithTag(HostLoginTags.CODE).performTextInput(code)
        composeRule.onNodeWithTag(HostLoginTags.SUBMIT).performClick()
    }
}
