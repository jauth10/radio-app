package com.iu.radioapp.data.local

import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.RequestStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

// In-memory DAO doubles. The abstract DAOs are subclassed, so their @Transaction bodies
// run unchanged - but nothing rolls back here; that stays with the instrumented tests.

class InMemoryLocalStore {
    val outbox = MutableStateFlow<List<OutboxEntity>>(emptyList())
    val songRequests = MutableStateFlow<List<SongRequestEntity>>(emptyList())
}

class FakeOutboxDao(private val store: InMemoryLocalStore) : OutboxDao() {

    private var nextId = 1L

    override suspend fun insert(entry: OutboxEntity): Long {
        check(store.outbox.value.none { it.idempotencyKey == entry.idempotencyKey }) {
            "UNIQUE constraint failed: outbox.idempotency_key"
        }
        val id = nextId++
        store.outbox.value += entry.copy(id = id)
        return id
    }

    override fun observeAll(): Flow<List<OutboxEntity>> = store.outbox

    override fun observeByStatus(status: DeliveryStatus): Flow<List<OutboxEntity>> =
        store.outbox.map { entries -> entries.filter { it.status == status } }

    override suspend fun findEntry(entryId: Long): OutboxEntity? =
        store.outbox.value.firstOrNull { it.id == entryId }

    override suspend fun findByIdempotencyKey(idempotencyKey: String): OutboxEntity? =
        store.outbox.value.firstOrNull { it.idempotencyKey == idempotencyKey }

    override suspend fun entriesWithStatus(status: DeliveryStatus): List<OutboxEntity> =
        store.outbox.value.filter { it.status == status }

    override suspend fun recordAttempt(entryId: Long, attempts: Int, attemptedAt: Instant, status: DeliveryStatus) =
        update(entryId) { it.copy(attempts = attempts, lastAttemptAt = attemptedAt, status = status) }

    override suspend fun setStatus(entryId: Long, status: DeliveryStatus) =
        update(entryId) { it.copy(status = status) }

    override suspend fun setRejected(entryId: Long, status: DeliveryStatus, reason: String) =
        update(entryId) { it.copy(status = status, rejectionReason = reason) }

    override suspend fun insertRequest(request: SongRequestEntity) {
        check(store.songRequests.value.none { it.idempotencyKey == request.idempotencyKey }) {
            "UNIQUE constraint failed: song_request.idempotency_key"
        }
        store.songRequests.value += request
    }

    override suspend fun updateRequest(request: SongRequestEntity): Int =
        updateRequests(request.idempotencyKey) { request }

    override suspend fun rejectRequest(idempotencyKey: String, status: RequestStatus, reason: String): Int =
        updateRequests(idempotencyKey) { it.copy(status = status, rejectionReason = reason) }

    override suspend fun deleteAll() {
        store.outbox.value = emptyList()
    }

    private fun update(entryId: Long, change: (OutboxEntity) -> OutboxEntity) {
        store.outbox.value = store.outbox.value.map { if (it.id == entryId) change(it) else it }
    }

    private fun updateRequests(idempotencyKey: String, change: (SongRequestEntity) -> SongRequestEntity): Int {
        val matches = store.songRequests.value.count { it.idempotencyKey == idempotencyKey }
        store.songRequests.value =
            store.songRequests.value.map { if (it.idempotencyKey == idempotencyKey) change(it) else it }
        return matches
    }
}

class FakeSongRequestDao(private val store: InMemoryLocalStore) : SongRequestDao {

    override suspend fun insert(request: SongRequestEntity) {
        check(store.songRequests.value.none { it.idempotencyKey == request.idempotencyKey }) {
            "UNIQUE constraint failed: song_request.idempotency_key"
        }
        store.songRequests.value += request
    }

    override suspend fun upsertAll(requests: List<SongRequestEntity>) {
        val keys = requests.map { it.idempotencyKey }.toSet()
        store.songRequests.value = store.songRequests.value.filter { it.idempotencyKey !in keys } + requests
    }

    override fun observeRequests(listenerId: String): Flow<List<SongRequestEntity>> =
        store.songRequests.map { rows ->
            rows.filter { it.listenerId == listenerId }.sortedByDescending { it.createdAt }
        }

    override fun observeRequest(idempotencyKey: String): Flow<SongRequestEntity?> =
        store.songRequests.map { rows -> rows.firstOrNull { it.idempotencyKey == idempotencyKey } }

    override suspend fun findRequest(idempotencyKey: String): SongRequestEntity? =
        store.songRequests.value.firstOrNull { it.idempotencyKey == idempotencyKey }

    override suspend fun deleteAll() {
        store.songRequests.value = emptyList()
    }
}

class FakeTrackCacheDao : TrackCacheDao {

    val tracks = MutableStateFlow<Map<String, TrackCacheEntity>>(emptyMap())

    override suspend fun upsert(track: TrackCacheEntity) {
        tracks.value += track.trackId to track
    }

    override suspend fun upsertAll(tracks: List<TrackCacheEntity>) = tracks.forEach { upsert(it) }

    override fun observeTrack(trackId: String): Flow<TrackCacheEntity?> = tracks.map { it[trackId] }

    override fun observeTracks(): Flow<List<TrackCacheEntity>> =
        tracks.map { cached -> cached.values.sortedBy { it.title } }

    override suspend fun findTrack(trackId: String): TrackCacheEntity? = tracks.value[trackId]

    override suspend fun deleteAll() {
        tracks.value = emptyMap()
    }
}

class FakePlaybackHistoryDao : PlaybackHistoryDao() {

    val rows = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())

    override fun observeHistory(): Flow<List<PlaybackHistoryEntity>> = rows

    override fun observeLatest(): Flow<PlaybackHistoryEntity?> = rows.map { it.firstOrNull() }

    override suspend fun count(): Int = rows.value.size

    override suspend fun insert(entry: PlaybackHistoryEntity) {
        rows.value = sorted(rows.value.filter { it.playbackId != entry.playbackId } + entry)
    }

    override suspend fun trimToLimit(limit: Int) {
        rows.value = rows.value.take(limit)
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    private fun sorted(entries: List<PlaybackHistoryEntity>) =
        entries.sortedWith(
            compareByDescending<PlaybackHistoryEntity> { it.startedAt }.thenByDescending { it.playbackId }
        )
}
