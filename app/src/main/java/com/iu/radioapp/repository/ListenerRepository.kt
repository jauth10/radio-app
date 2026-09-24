package com.iu.radioapp.repository

import com.iu.radioapp.data.local.UserPreferencesDataSource
import com.iu.radioapp.domain.Listener
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ListenerRepository @Inject constructor(
    private val preferences: UserPreferencesDataSource,
) {

    suspend fun getListener(): Outcome<Listener> = Outcome.Success(
        Listener(
            listenerId = preferences.requireListenerId(),
            displayName = preferences.displayName.first(),
        )
    )

    suspend fun setDisplayName(displayName: String?): Outcome<Unit> {
        preferences.setDisplayName(displayName)
        return Outcome.Success(Unit)
    }
}
