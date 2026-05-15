package com.timeflux.util

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Generates Universally Unique Lexicographically Sortable Identifiers (ULIDs).
 *
 * A ULID is a 26-character Crockford Base32 string encoding:
 *   - 48 bits of millisecond timestamp  → lexicographic sort == chronological sort
 *   - 80 bits of cryptographic randomness → negligible collision probability
 *
 * Use [generate] whenever a new [com.timeflux.domain.model.TimelineEntry] id is needed.
 * The time-ordering property means cursor pagination works correctly even if the host clock
 * is imprecise — two ULIDs generated in the same millisecond will still differ in the
 * random portion and sort stably.
 */
object Ulid {

    // Crockford Base32: no I, L, O, U to avoid visual ambiguity
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * Generate a new ULID string.
     *
     * @param timestamp  milliseconds since Unix epoch (defaults to now)
     * @param random     entropy source (defaults to [Random.Default])
     */
    fun generate(
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        random: Random = Random.Default,
    ): String {
        val result = CharArray(26)

        // ---- Timestamp: 48 bits → 10 Base32 chars (positions 0–9) ----
        var ts = timestamp
        for (i in 9 downTo 0) {
            result[i] = ALPHABET[(ts and 0x1F).toInt()]
            ts = ts ushr 5
        }

        // ---- Randomness: 80 bits (10 bytes) → 16 Base32 chars (positions 10–25) ----
        // Process in two 5-byte (40-bit) chunks → 8 chars each.
        val rnd = random.nextBytes(10)
        encodePart(rnd, byteOffset = 0, destOffset = 10, result)
        encodePart(rnd, byteOffset = 5, destOffset = 18, result)

        return String(result)
    }

    /** Pack 5 bytes into a 40-bit value and write 8 Base32 chars into [out] at [destOffset]. */
    private fun encodePart(bytes: ByteArray, byteOffset: Int, destOffset: Int, out: CharArray) {
        var v = 0L
        for (b in byteOffset until byteOffset + 5) {
            v = (v shl 8) or (bytes[b].toLong() and 0xFF)
        }
        for (i in destOffset + 7 downTo destOffset) {
            out[i] = ALPHABET[(v and 0x1F).toInt()]
            v = v ushr 5
        }
    }
}
