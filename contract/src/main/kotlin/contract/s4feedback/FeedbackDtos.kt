package contract.s4feedback

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * S4 – Feedback & Hörerforschung.
 *   POST /bewertungen                          -> BewertungResponse  (Idempotenz-Schlüssel)
 *   GET  /bewertungen/aggregat?sendungId=      -> AggregatDto
 *   GET  /bewertungen?seit=&sendungId=         -> List<BewertungsEreignisDto>  (POLLING-Rückfall)
 *   WS   /ereignisse/bewertungen               -> NEUE_BEWERTUNG / AGGREGAT_AKTUALISIERT
 */

/**
 * Request zum Absenden einer Bewertung. Trägt 'Idempotenz-Schluessel'.
 *
 * bezugId: bei gegenstand=PLAYLIST die sendungId, bei
 * gegenstand=MODERATION die moderationId.
 * wert: 1..5, außerhalb -> 422.
 * zeitpunkt: Erstellzeitpunkt auf dem GERÄT (offline-fähig).
 */
@Serializable
data class BewertungRequest(
    val gegenstand: Bewertungsgegenstand,
    val bezugId: String,
    val wert: Int,
    val kommentar: String?,
    val hoererId: String,
    val zeitpunkt: Instant,
)

/**
 * Antwort auf eine akzeptierte Bewertung.
 * Eine Ablehnung kommt serverseitig als 409/422 mit FehlerDto; 
 * eine Erfolgsantwort (2xx) impliziert bereits die Annahme.
 */
@Serializable
data class BewertungResponse(
    val bewertungId: String,
)

/**
 * Aggregatwerte für eine Sendung.
 */
@Serializable
data class AggregatDto(
    val durchschnittPlaylist: Double,
    val anzahlPlaylist: Int,
    val durchschnittModeration: Double,
    val anzahlModeration: Int,
    val zeitfenster: Zeitfenster,
)

@Serializable
data class Zeitfenster(
    val von: Instant,
    val bis: Instant,
)

/**
 * Ein Bewertungsereignis – identische Form für den WebSocket-Push UND die
 * Polling-Antwort (GET /bewertungen?seit=). Eine Form, zwei Transportwege.
 *
 * Wichtig zur Latenzmessung: t0 ist der EMPFANGSzeitpunkt auf dem
 * SERVER, nicht das Client-Feld aus dem Request. Nur so misst t1 - t0 die
 * echte Ende-zu-Ende-Latenz und nicht die Uhrenabweichung des Geräts.
 */
@Serializable
data class BewertungsEreignisDto(
    val bewertungId: String,
    val gegenstand: Bewertungsgegenstand,
    val wert: Int,
    val kommentar: String?,
    val t0: Instant,          // Server-Empfangszeitpunkt (Latenz-Messpunkt)
    val anzeigename: String?,
)

@Serializable
enum class Bewertungsgegenstand {
    PLAYLIST,
    MODERATION,
}

/** Nachrichtentypen im WebSocket-Kanal. */
@Serializable
enum class EreignisTyp {
    NEUE_BEWERTUNG,
    AGGREGAT_AKTUALISIERT,
}
