package com.iu.radioapp.interactor

import com.iu.radioapp.data.local.FakeOutboxDao
import com.iu.radioapp.data.local.FakePlaybackHistoryDao
import com.iu.radioapp.data.local.FakeSongRequestDao
import com.iu.radioapp.data.local.FakeTrackCacheDao
import com.iu.radioapp.data.local.InMemoryLocalStore
import com.iu.radioapp.data.local.inMemoryUserPreferences
import com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource
import com.iu.radioapp.data.remote.s2archive.FakeArchiveDataSource
import com.iu.radioapp.data.remote.s3requests.FakeRequestsDataSource
import com.iu.radioapp.data.remote.s4feedback.FakeFeedbackDataSource
import com.iu.radioapp.domain.Track
import com.iu.radioapp.repository.ArchiveRepository
import com.iu.radioapp.repository.HostRepository
import com.iu.radioapp.repository.ListenerRepository
import com.iu.radioapp.repository.MutableClock
import com.iu.radioapp.repository.RatingRepository
import com.iu.radioapp.repository.SongRequestRepository
import com.iu.radioapp.repository.TrackRepository

class RecordingDeliveryScheduler : DeliveryScheduler {

    var calls = 0
        private set

    override fun schedule() {
        calls++
    }
}

class InteractorFixture {
    val clock = MutableClock()
    val playout = FakePlayoutDataSource(clock)
    val archive = FakeArchiveDataSource()
    val requests = FakeRequestsDataSource()
    val feedback = FakeFeedbackDataSource(clock)
    val store = InMemoryLocalStore()
    val outboxDao = FakeOutboxDao(store)
    val songRequestDao = FakeSongRequestDao(store)
    val trackCacheDao = FakeTrackCacheDao()
    val historyDao = FakePlaybackHistoryDao()
    val preferences = inMemoryUserPreferences()
    val scheduler = RecordingDeliveryScheduler()

    val trackRepository = TrackRepository(playout, trackCacheDao, historyDao, clock)
    val archiveRepository = ArchiveRepository(archive)
    val songRequestRepository = SongRequestRepository(requests, outboxDao, songRequestDao, clock)
    val ratingRepository = RatingRepository(feedback, outboxDao, clock)
    val hostRepository = HostRepository(playout, preferences)
    val listenerRepository = ListenerRepository(preferences)

    val trackInteractor = TrackInteractor(trackRepository, archiveRepository, clock)
    val songRequestInteractor =
        SongRequestInteractor(archiveRepository, songRequestRepository, listenerRepository, scheduler, clock)
    val ratingInteractor = RatingInteractor(trackRepository, ratingRepository, listenerRepository, scheduler, clock)
    val hostInteractor = HostInteractor(hostRepository, listenerRepository, trackRepository, ratingRepository)
}

fun track(trackId: String = "trk-1", broadcastable: Boolean? = null) = Track(
    trackId = trackId,
    artist = "The Fake Band",
    title = "Sample Song",
    album = "Fake Album",
    coverUrl = null,
    durationSeconds = 210,
    broadcastable = broadcastable,
)
