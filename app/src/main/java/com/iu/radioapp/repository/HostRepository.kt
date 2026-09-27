package com.iu.radioapp.repository

import com.iu.radioapp.data.local.UserPreferencesDataSource
import com.iu.radioapp.data.remote.s1playout.PlayoutDataSource
import com.iu.radioapp.domain.HostSession
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.repository.mapping.toHostSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HostRepository @Inject constructor(
    private val playout: PlayoutDataSource,
    private val preferences: UserPreferencesDataSource,
) {

    suspend fun login(hostCode: String, deviceId: String): Outcome<HostSession> =
        when (val outcome = playout.loginHost(hostCode, deviceId)) {
            is Outcome.Success -> {
                preferences.setHostSessionToken(outcome.value.sessionToken)
                Outcome.Success(outcome.value.toHostSession())
            }
            is Outcome.Error -> outcome
        }

    fun observeSessionActive(): Flow<Boolean> =
        preferences.hostSessionToken.map { it != null }

    suspend fun endSession(): Outcome<Unit> {
        preferences.clearHostSession()
        return Outcome.Success(Unit)
    }
}
