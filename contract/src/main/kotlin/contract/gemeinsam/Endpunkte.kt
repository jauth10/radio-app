package contract.gemeinsam

/**
 * Alle Endpunkt-Pfade und Header-Namen zentral.
 * App-Client und Stub-Server referenzieren dieselben Konstanten, damit sie
 * sich nicht auf abweichende Schreibweisen verlaufen können.
 */
object Endpunkte {

    // --- S1 Playout ---
    const val S1_AKTUELL = "/playout/aktuell"
    const val S1_VERLAUF = "/playout/verlauf"                  // ?limit=
    const val S1_ANMELDUNG = "/playout/moderation/anmeldung"   // POST

    // --- S2 Archiv ---
    const val S2_TITEL_SUCHE = "/archiv/titel"                 // ?q=&limit=
    const val S2_TITEL_DETAIL = "/archiv/titel/{titelId}"

    // --- S3 Wünsche ---
    const val S3_WUENSCHE = "/wuensche"                        // POST + GET ?hoererId=
    const val S3_WUNSCH_DETAIL = "/wuensche/{wunschId}"

    // --- S4 Feedback ---
    const val S4_BEWERTUNGEN = "/bewertungen"                  // POST
    const val S4_AGGREGAT = "/bewertungen/aggregat"            // ?sendungId=
    const val S4_BEWERTUNGEN_SEIT = "/bewertungen"             // GET ?seit=&sendungId=  (Polling-Rückfall)
    const val S4_WS_EREIGNISSE = "/ereignisse/bewertungen"     // WebSocket

    // --- Query-Parameter-Namen ---
    const val PARAM_Q = "q"
    const val PARAM_LIMIT = "limit"
    const val PARAM_HOERER_ID = "hoererId"
    const val PARAM_SENDUNG_ID = "sendungId"
    const val PARAM_SEIT = "seit"
    const val PARAM_TOKEN = "token"   // WebSocket: Token als Query-Parameter

    // --- Header ---
    const val HEADER_IDEMPOTENZ = "Idempotenz-Schluessel"   // nur POST /wuensche + POST /bewertungen
    const val HEADER_AUTH = "Authorization"                 // "Bearer {sitzungstoken}"
}
