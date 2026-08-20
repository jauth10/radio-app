package contract.s1playout

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * S1 – Playout & Sendeplan.
 *   GET  /playout/aktuell               -> AktuellerTitelDto
 *   GET  /playout/verlauf?limit=        -> List<VerlaufseintragDto>
 *   POST /playout/moderation/anmeldung  -> AnmeldungResponse
 */

/**
 * Aktuell laufender Titel.
 * moderationId und moderationName sind nullable, da unmoderierte 
 * Sendungen der Normalfall sind (Domänenbeziehung 0..1).
 */
@Serializable
data class AktuellerTitelDto(
    val titelId: String,
    val interpret: String,
    val titel: String,
    val album: String?,
    val coverUrl: String?,
    val dauerSekunden: Int,
    val startzeitpunkt: Instant,
    val sendungId: String,
    val moderationId: String?,      // nullable: unmoderierte Sendung
    val moderationName: String?,    // nullable: unmoderierte Sendung
)

/** Ein Eintrag im Verlauf (schlanker als der aktuelle Titel). */
@Serializable
data class VerlaufseintragDto(
    val titelId: String,
    val interpret: String,
    val titel: String,
    val album: String?,
    val startzeitpunkt: Instant,
)

/** Request der Moderationsanmeldung. Trägt KEINEN Idempotenz-Schlüssel. */
@Serializable
data class AnmeldungRequest(
    val moderationCode: String,
    val geraeteId: String,
)

/**
 * Antwort der Moderationsanmeldung.
 * Das sitzungstoken wird danach als 'Authorization: Bearer {token}' (HTTP)
 * bzw. als Query-Parameter (WebSocket) mitgeschickt.
 */
@Serializable
data class AnmeldungResponse(
    val sitzungstoken: String,
    val gueltigBis: Instant,
    val moderationId: String,
    val moderationName: String,
)
