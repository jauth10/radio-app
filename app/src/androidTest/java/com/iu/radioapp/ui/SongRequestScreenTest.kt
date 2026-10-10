package com.iu.radioapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.ui.request.RequestForm
import com.iu.radioapp.ui.request.SongRequestScreen
import com.iu.radioapp.ui.request.SongRequestTags
import com.iu.radioapp.ui.request.SongRequestUiState
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** US3, request form (RAD-19). */
@RunWith(AndroidJUnit4::class)
class SongRequestScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var submits = 0
    private val messages = mutableListOf<String>()
    private val names = mutableListOf<String>()

    private fun show(state: SongRequestUiState) = compose.setContent {
        RadioAppTheme {
            SongRequestScreen(
                state = state,
                onMessageChange = { messages += it },
                onDisplayNameChange = { names += it },
                onSubmit = { submits++ },
                onReload = {},
                onShowMyRequests = {},
            )
        }
    }

    private val form = RequestForm(track(), message = "", displayName = "")

    @Test
    fun ak_3_2_request_has_track_optional_message_and_optional_name() {
        // The fields are controlled, so the test holds the state the ViewModel would.
        var state by mutableStateOf<SongRequestUiState>(SongRequestUiState.Content(form))
        compose.setContent {
            RadioAppTheme {
                SongRequestScreen(
                    state = state,
                    onMessageChange = { text ->
                        messages += text
                        state = SongRequestUiState.Content((state as SongRequestUiState.Content).form.copy(message = text))
                    },
                    onDisplayNameChange = { text ->
                        names += text
                        state = SongRequestUiState.Content((state as SongRequestUiState.Content).form.copy(displayName = text))
                    },
                    onSubmit = { submits++ },
                    onReload = {},
                    onShowMyRequests = {},
                )
            }
        }

        compose.onNodeWithText("Sample Song").assertIsDisplayed()
        compose.onNodeWithTag(SongRequestTags.MESSAGE).performTextInput("Bitte laut")
        compose.onNodeWithTag(SongRequestTags.NAME).performTextInput("Kim")
        compose.onNodeWithTag(SongRequestTags.SUBMIT).assertIsEnabled().performClick()
        assertEquals("Bitte laut", messages.last())
        assertEquals("Kim", names.last())
        assertEquals(1, submits)
    }

    @Test
    fun ak_3_3_track_that_may_not_be_aired_is_refused_with_reason() {
        show(SongRequestUiState.Empty(track(broadcastable = false), RefusalReason.TRACK_NOT_BROADCASTABLE))

        compose.onNodeWithText(str(R.string.refusal_track_not_broadcastable)).assertIsDisplayed()
        compose.onAllNodesWithTag(SongRequestTags.SUBMIT).assertCountEquals(0)
    }

    @Test
    fun ak_3_4_unreachable_archive_still_allows_the_request() {
        show(SongRequestUiState.Offline(form.copy(track = track(broadcastable = null))))

        compose.onNodeWithText(str(R.string.request_archive_offline)).assertIsDisplayed()
        compose.onNodeWithTag(SongRequestTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun ak_3_5_request_is_queued_at_once() {
        show(SongRequestUiState.Offline(form.copy(queued = true)))

        compose.onNodeWithText(str(R.string.request_queued)).assertIsDisplayed()
        compose.onAllNodesWithTag(SongRequestTags.SUBMIT).assertCountEquals(0)
    }

    @Test
    fun rejection_by_the_archive_shows_the_reason() {
        show(SongRequestUiState.Error(Failure.Rejected("Unbekannter Titel", retryable = false), form = null))

        compose.onNodeWithText(str(R.string.failure_rejected, "Unbekannter Titel")).assertIsDisplayed()
    }

    @Test
    fun loading_state_is_shown() {
        show(SongRequestUiState.Loading)

        compose.onNodeWithText(str(R.string.state_loading)).assertIsDisplayed()
    }
}
