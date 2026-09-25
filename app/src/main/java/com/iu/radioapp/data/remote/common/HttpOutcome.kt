package com.iu.radioapp.data.remote.common

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.common.ErrorDto
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException

/**
 * Runs [request] and turns the result into [Outcome], following the status
 * code -> Failure mapping agreed in Failure.kt - the interface with Jasper's
 * repository layer.
 *
 * Only [IOException] is caught here, not a blanket [Exception]: a wider catch
 * would also swallow [kotlinx.coroutines.CancellationException], which every
 * data source in this app has to let propagate (see Outcome.kt).
 */
internal suspend inline fun <reified T> requestOutcome(request: suspend () -> HttpResponse): Outcome<T> {
    val response = try {
        request()
    } catch (e: IOException) {
        return Outcome.Error(Failure.Connection)
    }
    return response.toOutcome()
}

internal suspend inline fun <reified T> HttpResponse.toOutcome(): Outcome<T> =
    if (status.isSuccess()) Outcome.Success(body()) else Outcome.Error(toFailure())

internal suspend fun HttpResponse.toFailure(): Failure = when (status) {
    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> Failure.Unauthorized
    HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable -> Failure.Server
    else -> {
        val error = body<ErrorDto>()
        Failure.Rejected(reason = error.reason, retryable = error.retryable)
    }
}
