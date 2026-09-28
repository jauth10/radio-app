package com.iu.radioapp.data.remote.common

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import contract.common.ErrorDto
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
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

/**
 * A body that fails to decode - as [T] here, or as [ErrorDto] in [toFailure] -
 * is a protocol violation (no body at all, an unreadable content type, or JSON
 * in the wrong shape), not a business rejection and not a connection problem.
 * [NoTransformationFoundException] and [ContentConvertException] (which covers
 * JsonConvertException too) both land as a diagnostic, non-retryable rejection
 * instead of escaping as an exception - every data source promises that never
 * happens (see PlayoutDataSource).
 */
internal suspend inline fun <reified T> HttpResponse.toOutcome(): Outcome<T> =
    if (status.isSuccess()) {
        try {
            Outcome.Success(body())
        } catch (e: NoTransformationFoundException) {
            Outcome.Error(undecodableBodyRejection())
        } catch (e: ContentConvertException) {
            Outcome.Error(undecodableBodyRejection())
        }
    } else {
        Outcome.Error(toFailure())
    }

internal suspend fun HttpResponse.toFailure(): Failure = when {
    status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden -> Failure.Unauthorized
    // The full 5xx range, not just 500/503: a proxy's 502/504 is the same kind
    // of transient technical fault and must stay retryable, never Rejected.
    status.value in 500..599 -> Failure.Server
    else -> try {
        val error = body<ErrorDto>()
        Failure.Rejected(reason = error.reason, retryable = error.retryable)
    } catch (e: NoTransformationFoundException) {
        undecodableBodyRejection()
    } catch (e: ContentConvertException) {
        undecodableBodyRejection()
    }
}

private fun HttpResponse.undecodableBodyRejection(): Failure.Rejected =
    Failure.Rejected(reason = "HTTP ${status.value}", retryable = false)
