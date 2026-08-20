package contract.s2archiv

import kotlinx.serialization.Serializable

/**
 * S2 – Musikarchiv.
 *   GET /archiv/titel?q=&limit=   -> List<TitelDto>
 *   GET /archiv/titel/{titelId}   -> TitelDto
 *
 * Hinweis: Listen-Endpunkte ohne Paginierung geben direkt eine Liste zurück 
 * (kein Wrapper-Objekt wie TitelSucheResponse), damit die Struktur 
 * projektweit konsistent bleibt.
 */
@Serializable
data class TitelDto(
    val titelId: String,
    val interpret: String,
    val titel: String,
    val album: String?,
    val coverUrl: String?,
    val sendefaehig: Boolean,
)
