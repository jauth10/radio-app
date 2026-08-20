package contract.gemeinsam

import kotlinx.serialization.json.Json

/**
 * Zentrale JSON-Konfiguration, von App-Client UND Stub-Server identisch zu nutzen.
 *
 * Wichtige Architektur-Festlegungen:
 *  - kotlinx.serialization als Serialisierung (geteiltes Modul + Ktor beidseitig)
 *  - kotlinx.datetime.Instant für alle Zeitpunkte (NICHT java.time.Instant –
 *    das hat keinen kotlinx-Serializer). ISO-8601, immer UTC mit 'Z'.
 *  - explicitNulls = false: leere Felder werden weggelassen statt als null
 *    gesendet. Hält das JSON schlank und trennt "Feld fehlt" von "Feld ist null".
 *
 * Hinweis zu Instant: kotlinx.datetime.Instant bringt seinen Serializer mit,
 * sobald die Abhängigkeit kotlinx-datetime im Modul liegt. Die Instant-Felder
 * in den DTOs brauchen dann keine eigene @Serializable-Annotation.
 */
val RadioJson: Json = Json {
    explicitNulls = false
    ignoreUnknownKeys = true   // Vorwärtskompatibilität: unbekannte Felder brechen nicht
    encodeDefaults = true
}
