package contract.common

import kotlinx.serialization.json.Json

/**
 * Central JSON configuration, used identically by the app client AND the stub server.
 *
 * Key architecture decisions:
 *  - kotlinx.serialization as the serialization library (shared module + Ktor on both sides)
 *  - kotlin.time.Instant (stdlib) for all points in time, NOT java.time.Instant
 *    (no kotlinx serializer) and not kotlinx.datetime.Instant (now just a
 *    deprecated typealias for kotlin.time.Instant). ISO-8601, always UTC with 'Z'.
 *  - explicitNulls = false: empty fields are omitted instead of sent as
 *    null. Keeps the JSON lean and separates "field missing" from "field is null".
 *
 * Note on Instant: kotlinx-serialization-json ships a built-in serializer for
 * kotlin.time.Instant, no extra dependency needed. The type is still marked
 * @ExperimentalTime, so every module consuming it needs the corresponding
 * opt-in (see contract/build.gradle.kts).
 */
val RadioJson: Json = Json {
    explicitNulls = false
    ignoreUnknownKeys = true   // forward compatibility: unknown fields don't break parsing
    encodeDefaults = true
}
