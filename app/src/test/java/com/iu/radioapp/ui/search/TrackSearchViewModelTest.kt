package com.iu.radioapp.ui.search

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackSearchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()
    private val viewModel = TrackSearchViewModel(fixture.songRequestInteractor)

    private fun searchFor(query: String) {
        viewModel.onQueryChange(query)
        viewModel.search()
    }

    @Test
    fun `starts empty with a prompt`() {
        assertEquals(TrackSearchUiState.Empty(query = "", searchedFor = null), viewModel.uiState.value)
    }

    @Test
    fun `loading turns into the hits`() = runTest {
        searchFor("song")
        assertEquals(TrackSearchUiState.Loading("song"), viewModel.uiState.value)

        advanceUntilIdle()

        val state = viewModel.uiState.value as TrackSearchUiState.Content
        assertEquals(listOf("trk-1", "trk-2", "trk-3"), state.hits.map { it.trackId })
    }

    @Test
    fun `hits carry the archive flag for tracks that may not be requested`() = runTest {
        searchFor("third")
        advanceUntilIdle()

        val hit = (viewModel.uiState.value as TrackSearchUiState.Content).hits.single()
        assertEquals(false, hit.broadcastable)
    }

    @Test
    fun `blank query does not reach the archive`() = runTest {
        fixture.archive.nextFailure = Failure.Server

        searchFor("   ")
        advanceUntilIdle()

        assertEquals(TrackSearchUiState.Empty(query = "   ", searchedFor = null), viewModel.uiState.value)
        assertEquals(Failure.Server, fixture.archive.nextFailure)
    }

    @Test
    fun `no hits is empty and names the term`() = runTest {
        searchFor("nothing like this")
        advanceUntilIdle()

        assertEquals(TrackSearchUiState.Empty("nothing like this", searchedFor = "nothing like this"), viewModel.uiState.value)
    }

    @Test
    fun `no connection is offline`() = runTest {
        fixture.archive.nextFailure = Failure.Connection

        searchFor("song")
        advanceUntilIdle()

        assertEquals(TrackSearchUiState.Offline("song"), viewModel.uiState.value)
    }

    @Test
    fun `server fault is an error`() = runTest {
        fixture.archive.nextFailure = Failure.Server

        searchFor("song")
        advanceUntilIdle()

        assertEquals(TrackSearchUiState.Error("song", Failure.Server), viewModel.uiState.value)
    }

    @Test
    fun `rejection is an error carrying the station reason`() = runTest {
        fixture.archive.nextFailure = Failure.Rejected("Suchbegriff zu kurz", retryable = false)

        searchFor("s")
        advanceUntilIdle()

        val state = viewModel.uiState.value as TrackSearchUiState.Error
        assertEquals("Suchbegriff zu kurz", (state.failure as Failure.Rejected).reason)
    }

    @Test
    fun `typing keeps the current results`() = runTest {
        searchFor("song")
        advanceUntilIdle()

        viewModel.onQueryChange("song 2")

        val state = viewModel.uiState.value
        assertTrue(state is TrackSearchUiState.Content)
        assertEquals("song 2", state.query)
    }
}
