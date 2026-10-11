package com.iu.radioapp.repository

import com.iu.radioapp.data.local.UserPreferencesDataSource
import com.iu.radioapp.data.remote.s1playout.PlayoutDataSource
import com.iu.radioapp.domain.HostSession
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.SessionCredentials
import com.iu.radioapp.repository.mapping.toHostSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HostRepository @Inject constructor(
    private val playout: PlayoutDataSource,
    private val preferences: UserPreferencesDataSource,
) {

    suspend fun login(hostCode: String, deviceId: String): Outcome<HostSession> =
        when (val outcome = playout.loginHost(hostCode, deviceId)) {
            is Outcome.Success -> {
                preferences.setHostSession(outcome.value.sessionToken, outcome.value.validUntil)
                Outcome.Success(outcome.value.toHostSession())
            }
            is Outcome.Error -> outcome
        }

    // The only reader of the token; whoever needs it gets it handed in by the interactor.
    fun observeSessionToken(): Flow<String?> = preferences.hostSessionToken

    // Distinct, so that unrelated preference writes do not restart whoever follows the session.
    fun observeSession(): Flow<SessionCredentials?> =
        preferences.hostSession
            .map { stored -> stored?.let { SessionCredentials(it.token, it.validUntil) } }
            .distinctUntilChanged()

    fun observeSessionActive(): Flow<Boolean> =
        observeSessionToken().map { it != null }

    suspend fun endSession(): Outcome<Unit> {
        preferences.clearHostSession()
        return Outcome.Success(Unit)
    }
}
