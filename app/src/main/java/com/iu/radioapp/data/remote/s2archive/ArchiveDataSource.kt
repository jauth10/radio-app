package com.iu.radioapp.data.remote.s2archive

import com.iu.radioapp.domain.Outcome
import contract.s2archive.TrackDto

/** S2 - Music archive. See [contract.s2archive] for the wire format. */
interface ArchiveDataSource {

    suspend fun searchTracks(query: String, limit: Int): Outcome<List<TrackDto>>

    suspend fun getTrackDetail(trackId: String): Outcome<TrackDto>
}
