package com.timeflux.module.mood

import com.timeflux.data.json.AppJson
import com.timeflux.domain.model.TimelineEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Module-specific payload stored as a JSON blob in [TimelineEntry.payload].
 *
 * [score] is the only required field; everything else is optional and enriches
 * the entry without breaking existing rows when new fields are added.
 */
@Serializable
data class MoodPayload(
    /** Overall mood on a 1–5 scale (1 = very low, 5 = very high). */
    val score: Int,
    /** Optional energy level on a 1–5 scale (1 = drained, 5 = energized).
     *  Tracked separately so mood and energy can diverge (e.g. tired but happy). */
    val energy: Int? = null,
    /** Legacy free-form emotion tag — retained for backward compat with existing entries. */
    val emotion: String? = null,
    /** Multi-selected emotions from the curated vocabulary (e.g. ["Calm", "Grateful"]). */
    val emotions: List<String> = emptyList(),
    /** Contextual factors that influenced this check-in (e.g. ["Sleep", "Exercise"]). */
    val factors: List<String> = emptyList(),
    /** Denormalized tag names for card display; entry_tags is the canonical filter index. */
    val tags: List<String> = emptyList(),
)

/** Decode this entry's JSON payload into a typed [MoodPayload]. */
fun TimelineEntry.moodPayload(json: Json = AppJson): MoodPayload =
    json.decodeFromString(payload)
