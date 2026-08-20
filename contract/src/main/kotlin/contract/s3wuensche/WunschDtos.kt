package contract.s3wuensche

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * S3 – Wunschmanagement.
 *   POST /wuensche                 -> WunschResponse   (trägt Idempotenz-Schlüssel)
 *   GET  /wuensche/{wunschId}       -> WunschStatusDto
*    GET  /wuensche?hoererId=        -> List<WunschUebersichtDto>
 */

/** Request zum Absenden eines Wunsches. Trägt den Header 'Idempotenz-Schluessel'. */
@Serializable
data class WunschRequest(
    val titelId: String,
    val hoererId: String,
    val anzeigename: String?,
    val nachricht: String?,
    val zeitpunkt: Instant,   // Erstellzeitpunkt auf dem GERÄT (wichtig für offline)
)

@Serializable
data class WunschResponse(
    val wunschId: String,
    val status: WunschStatus,
    val geplanteAusstrahlung: Instant?,
)

@Serializable
data class WunschStatusDto(
    val status: WunschStatus,
    val grund: String?,
    val geplanteAusstrahlung: Instant?,
)

/**
 * Übersichtszeile in GET /wuensche?hoererId=.
 * 'titelAnzeige' enthält den anzuzeigenden Titelnamen als String (kein Objekt).
 */
@Serializable
data class WunschUebersichtDto(
    val wunschId: String,
    val titelAnzeige: String,
    val status: WunschStatus,
)

/** Status eines Wunsches auf SERVER-Seite (eingegangen -> ... -> entschieden). */
@Serializable
enum class WunschStatus {
    AUSSTEHEND,
    IN_PRUEFUNG,
    ANGENOMMEN,
    ABGELEHNT,
}
