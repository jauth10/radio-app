package com.iu.radioapp.data.remote.s4feedback

import com.iu.radioapp.data.remote.common.requestOutcome
import com.iu.radioapp.domain.Outcome
import contract.common.Endpoints
import contract.s4feedback.AggregateDto
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingRequest
import contract.s4feedback.RatingResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class HttpFeedbackDataSource @Inject constructor(
    private val client: HttpClient,
) : FeedbackDataSource {

    override suspend fun submitRating(idempotencyKey: String, rating: RatingRequest): Outcome<RatingResponse> =
        requestOutcome {
            client.post(Endpoints.S4_RATINGS) {
                header(Endpoints.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                contentType(ContentType.Application.Json)
                setBody(rating)
            }
        }

    override suspend fun getAggregate(showId: String): Outcome<AggregateDto> =
        requestOutcome {
            client.get(Endpoints.S4_AGGREGATE) {
                parameter(Endpoints.PARAM_SHOW_ID, showId)
            }
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun getRatingsSince(since: Instant, showId: String): Outcome<List<RatingEventDto>> =
        requestOutcome {
            client.get(Endpoints.S4_RATINGS_SINCE) {
                parameter(Endpoints.PARAM_SINCE, since.toString())
                parameter(Endpoints.PARAM_SHOW_ID, showId)
            }
        }
}
