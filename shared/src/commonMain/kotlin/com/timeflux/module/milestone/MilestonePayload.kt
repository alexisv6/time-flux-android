package com.timeflux.module.milestone

import com.timeflux.data.json.AppJson
import com.timeflux.domain.model.TimelineEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Broad category for a milestone entry — drives icon and color defaults in the UI.
 * Serialized as its [id] string so DB payloads remain human-readable.
 */
@Serializable
enum class MilestoneCategory(val id: String) {
    @SerialName("personal")   PERSONAL("personal"),
    @SerialName("career")     CAREER("career"),
    @SerialName("education")  EDUCATION("education"),
    @SerialName("health")     HEALTH("health"),
    @SerialName("family")     FAMILY("family"),
    @SerialName("travel")     TRAVEL("travel"),
    @SerialName("financial")  FINANCIAL("financial"),
    @SerialName("other")      OTHER("other");
}

/**
 * Module-specific payload stored as a JSON blob in [TimelineEntry.payload].
 *
 * All fields default so that existing rows remain decodable when new fields are added.
 */
@Serializable
data class MilestonePayload(
    val category: MilestoneCategory = MilestoneCategory.PERSONAL,
    /** Optional emoji shown as the entry's visual icon (e.g. "🎓", "💼"). */
    val emoji: String? = null,
    /** Optional hex accent color override for this entry (e.g. "#E91E63"). */
    val color: String? = null,
    /** Denormalized tag names for card display; entry_tags is the canonical filter index. */
    val tags: List<String> = emptyList(),
)

/** Decode this entry's JSON payload into a typed [MilestonePayload]. */
fun TimelineEntry.milestonePayload(json: Json = AppJson): MilestonePayload =
    json.decodeFromString(payload)
