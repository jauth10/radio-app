package com.iu.radioapp.data.remote.s3requests

import com.iu.radioapp.data.remote.common.requestOutcome
import com.iu.radioapp.domain.Outcome
import contract.common.Endpoints
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

class HttpRequestsDataSource @Inject constructor(
    private val client: HttpClient,
) : RequestsDataSource {

    override suspend fun submitRequest(
        idempotencyKey: String,
        request: CreateSongRequestDto,
    ): Outcome<SongRequestResponse> =
        requestOutcome {
            client.post(Endpoints.S3_REQUESTS) {
                header(Endpoints.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun getRequestStatus(requestId: String): Outcome<SongRequestStatusDto> =
        requestOutcome {
            client.get(Endpoints.S3_REQUEST_DETAIL.replace("{requestId}", requestId))
        }

    override suspend fun getRequestsForListener(listenerId: String): Outcome<List<SongRequestOverviewDto>> =
        requestOutcome {
            client.get(Endpoints.S3_REQUESTS) {
                parameter(Endpoints.PARAM_LISTENER_ID, listenerId)
            }
        }
}
