package com.iu.radioapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Track
import com.iu.radioapp.ui.search.TrackSearchScreen
import com.iu.radioapp.ui.search.TrackSearchTags
import com.iu.radioapp.ui.search.TrackSearchUiState
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** US3, search part (RAD-19). The 20-hit cap and the blank query are interactor and ViewModel tests. */
@RunWith(AndroidJUnit4::class)
class TrackSearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var searches = 0
    private val selected = mutableListOf<Track>()

    private fun show(state: TrackSearchUiState) = compose.setContent {
        RadioAppTheme { TrackSearchScreen(state, onQueryChange = {}, onSearch = { searches++ }, onTrackSelected = { selected += it }) }
    }

    @Test
    fun ak_3_1_hits_are_listed_and_selectable() {
        show(TrackSearchUiState.Content("song", listOf(track("trk-1", "Sample Song"), track("trk-2", "Second Song"))))

        compose.onAllNodesWithTag(TrackSearchTags.HIT).assertCountEquals(2)
        compose.onNodeWithText("Second Song").performClick()
        assertEquals("trk-2", selected.single().trackId)
    }

    @Test
    fun ak_3_3_track_that_may_not_be_aired_is_marked() {
        show(TrackSearchUiState.Content("third", listOf(track("trk-3", "Third Song", broadcastable = false))))

        compose.onNodeWithText(str(R.string.not_broadcastable_label)).assertIsDisplayed()
    }

    @Test
    fun empty_before_the_first_search_prompts_for_a_term() {
        show(TrackSearchUiState.Empty("", searchedFor = null))

        compose.onNodeWithText(str(R.string.search_prompt)).assertIsDisplayed()
    }

    @Test
    fun empty_after_a_search_names_the_term() {
        show(TrackSearchUiState.Empty("xyz", searchedFor = "xyz"))

        compose.onNodeWithText(str(R.string.search_no_hits, "xyz")).assertIsDisplayed()
    }

    @Test
    fun offline_points_to_requesting_from_the_programme() {
        show(TrackSearchUiState.Offline("song"))

        compose.onNodeWithText(str(R.string.search_offline_body)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_reload)).performClick()
        assertEquals(1, searches)
    }

    @Test
    fun rejection_shows_the_station_reason() {
        show(TrackSearchUiState.Error("s", Failure.Rejected("Suchbegriff zu kurz", retryable = false)))

        compose.onNodeWithText(str(R.string.failure_rejected, "Suchbegriff zu kurz")).assertIsDisplayed()
    }
}
