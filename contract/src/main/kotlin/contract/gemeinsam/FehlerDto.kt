package contract.gemeinsam

import kotlinx.serialization.Serializable

/**
 * Gemeinsames Fehler-DRAHTFORMAT für alle Systeme (S1–S4).
 *
 * Wichtig: FehlerDto ist NUR das Transportformat über die
 * Leitung. Es wandert bewusst NICHT bis ins Repository. Die DataSource
 * übersetzt einen HTTP-Fehler sofort in eine der vier App-Fehlerklassen
 * (Fehlerklasse, liegt in app/data/remote/). `grund` und `wiederholbar`
 * landen dann in der Fehlerklasse FachlicheAblehnung.
 *
 * Grund für die Trennung: Wandert FehlerDto (mit HTTP-Semantik) nach oben,
 * müsste die Zuordnung "welche Fehlerklasse ist das?" zweimal passieren.
 */
@Serializable
data class FehlerDto(
    val grund: String,
    val wiederholbar: Boolean,
)
