package com.iu.radioapp.data.remote.s2archive

import com.iu.radioapp.data.remote.common.requestOutcome
import com.iu.radioapp.domain.Outcome
import contract.common.Endpoints
import contract.s2archive.TrackDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject

class HttpArchiveDataSource @Inject constructor(
    private val client: HttpClient,
) : ArchiveDataSource {

    override suspend fun searchTracks(query: String, limit: Int): Outcome<List<TrackDto>> =
        requestOutcome {
            client.get(Endpoints.S2_TRACK_SEARCH) {
                parameter(Endpoints.PARAM_Q, query)
                parameter(Endpoints.PARAM_LIMIT, limit)
            }
        }

    override suspend fun getTrackDetail(trackId: String): Outcome<TrackDto> =
        requestOutcome {
            client.get(Endpoints.S2_TRACK_DETAIL.replace("{trackId}", trackId))
        }
}
