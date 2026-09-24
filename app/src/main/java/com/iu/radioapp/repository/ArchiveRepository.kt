package com.iu.radioapp.repository

import com.iu.radioapp.data.remote.s2archive.ArchiveDataSource
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Track
import com.iu.radioapp.domain.map
import com.iu.radioapp.repository.mapping.toTrack
import javax.inject.Inject

class ArchiveRepository @Inject constructor(
    private val archive: ArchiveDataSource,
) {

    suspend fun searchTracks(query: String, limit: Int): Outcome<List<Track>> =
        archive.searchTracks(query, limit).map { tracks -> tracks.map { it.toTrack() } }

    suspend fun getTrack(trackId: String): Outcome<Track> =
        archive.getTrackDetail(trackId).map { it.toTrack() }
}
