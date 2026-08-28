package com.iu.radioapp.data.remote.s1playout

import com.iu.radioapp.domain.Outcome
import contract.s1playout.CurrentTrackDto
import contract.s1playout.HistoryEntryDto
import contract.s1playout.HostLoginResponse

/**
 * S1 - Playout & schedule. See [contract.s1playout] for the wire format.
 *
 * Every method returns [Outcome]: a failure is a [com.iu.radioapp.domain.Failure]
 * value, never a thrown exception, and the wire-level ErrorDto never leaves the
 * implementation of this interface.
 */
interface PlayoutDataSource {

    suspend fun getCurrentTrack(): Outcome<CurrentTrackDto>

    suspend fun getHistory(limit: Int): Outcome<List<HistoryEntryDto>>

    suspend fun loginHost(hostCode: String, deviceId: String): Outcome<HostLoginResponse>
}
