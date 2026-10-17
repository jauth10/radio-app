package com.iu.radioapp.repository

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Only around Room and DataStore calls; a data source already answers with an Outcome.
internal suspend fun <T> localOutcome(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Error(Failure.Local)
    }

// The failed flow ends with the error; a reload has to collect it again.
internal fun <T> Flow<T>.asLocalOutcomes(): Flow<Outcome<T>> =
    map<T, Outcome<T>> { Outcome.Success(it) }
        .catch { e ->
            if (e is CancellationException || e !is Exception) throw e
            emit(Outcome.Error(Failure.Local))
        }
