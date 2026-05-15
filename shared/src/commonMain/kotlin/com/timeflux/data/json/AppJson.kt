package com.timeflux.data.json

import kotlinx.serialization.json.Json

/**
 * Shared [Json] instance for encoding and decoding module payload blobs.
 *
 * Configuration rationale:
 *  - [encodeDefaults] = true  — always write fields with default values so that payloads
 *    remain decodable if a new version adds a field with a default (avoids missing-key errors).
 *  - [ignoreUnknownKeys] = true — a payload written by a newer app version may contain
 *    fields this version doesn't know about; ignore them rather than crash.
 *  - [isLenient] = false — strict parsing; corrupt payloads surface as exceptions, not
 *    silently coerced to garbage values.
 */
val AppJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = false
}
