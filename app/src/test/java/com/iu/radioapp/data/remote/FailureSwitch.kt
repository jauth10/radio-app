package com.iu.radioapp.data.remote

import com.iu.radioapp.domain.Failure

/**
 * Failure trigger shared by the fakes.
 *
 * Set [nextFailure] to fail the next call with that class. Set [times] > 1
 * beforehand to fail that many calls in a row instead of just one - e.g. to
 * exercise a repository's retry loop rather than only its first attempt.
 */
class FailureSwitch {

    var nextFailure: Failure? = null
    var times: Int = 1

    fun consume(): Failure? {
        val failure = nextFailure ?: return null
        if (times > 1) {
            times -= 1
        } else {
            nextFailure = null
            times = 1
        }
        return failure
    }
}
