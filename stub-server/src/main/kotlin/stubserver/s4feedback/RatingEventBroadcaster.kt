package stubserver.s4feedback

import contract.common.RadioJson
import contract.s4feedback.AggregateDto
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingEventMessage
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Connected moderation sessions on /events/ratings, and the broadcast to all
 * of them. One stub process, a handful of demo sessions - a plain concurrent
 * set is enough, no dedicated actor or mutex needed. No per-show filtering:
 * the whole stub models a single show/host pairing (see FeedbackStore), so
 * every connected session gets every event.
 */
@OptIn(ExperimentalTime::class)
object RatingEventBroadcaster {

    private val sessions: MutableSet<DefaultWebSocketServerSession> = ConcurrentHashMap.newKeySet()

    fun register(session: DefaultWebSocketServerSession) {
        sessions += session
    }

    fun unregister(session: DefaultWebSocketServerSession) {
        sessions -= session
    }

    /**
     * A new rating always changes the aggregate too, so both messages go out
     * together rather than the aggregate being its own independently
     * triggered event.
     */
    suspend fun broadcastNewRating(rating: RatingEventDto, aggregate: AggregateDto, t0: Instant) {
        broadcast(RatingEventMessage(type = EventType.NEW_RATING, t0 = t0, rating = rating))
        broadcast(RatingEventMessage(type = EventType.AGGREGATE_UPDATED, t0 = t0, aggregate = aggregate))
    }

    private suspend fun broadcast(message: RatingEventMessage) {
        val text = RadioJson.encodeToString(message)
        for (session in sessions) {
            try {
                session.send(Frame.Text(text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A dead/closing session is cleaned up by its own webSocket
                // block's finally; one failed send must not stop the rest.
            }
        }
    }
}
