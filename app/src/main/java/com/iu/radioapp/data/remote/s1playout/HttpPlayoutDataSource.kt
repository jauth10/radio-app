package com.iu.radioapp.data.remote.s1playout

import com.iu.radioapp.data.remote.common.requestOutcome
import com.iu.radioapp.data.remote.common.toOutcome
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.common.Endpoints
import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import contract.s1playout.HostLoginRequest
import contract.s1playout.HostLoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject

class HttpPlayoutDataSource @Inject constructor(
    private val client: HttpClient,
) : PlayoutDataSource {

    override suspend fun getCurrentTrack(): Outcome<CurrentTrackDto?> {
        val response = try {
            client.get(Endpoints.S1_CURRENT)
        } catch (e: IOException) {
            return Outcome.Error(Failure.Connection)
        }
        return if (response.status == HttpStatusCode.NoContent) {
            Outcome.Success(null)
        } else {
            response.toOutcome()
        }
    }

    override suspend fun getHistory(limit: Int): Outcome<List<HistoryEntryDto>> =
        requestOutcome {
            client.get(Endpoints.S1_HISTORY) {
                parameter(Endpoints.PARAM_LIMIT, limit)
            }
        }

    override suspend fun loginHost(hostCode: String, deviceId: String): Outcome<HostLoginResponse> =
        requestOutcome {
            client.post(Endpoints.S1_HOST_LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(HostLoginRequest(hostCode = hostCode, deviceId = deviceId))
            }
        }
}
