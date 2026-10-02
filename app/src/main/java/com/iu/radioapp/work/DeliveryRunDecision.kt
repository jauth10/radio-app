package com.iu.radioapp.work

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.CancellationException

enum class NextRun { DONE, RETRY }

suspend fun captureDelivery(deliver: suspend () -> Outcome<Unit>): Result<Outcome<Unit>> =
    try {
        Result.success(deliver())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

fun nextRunAfter(results: List<Result<Outcome<Unit>>>): NextRun =
    if (results.any(::needsRetry)) NextRun.RETRY else NextRun.DONE

// A rejection is final and never worth another run, whether it arrives booked as Success or as an error.
private fun needsRetry(result: Result<Outcome<Unit>>): Boolean {
    val outcome = result.getOrElse { return true }
    return outcome is Outcome.Error && outcome.failure !is Failure.Rejected
}
