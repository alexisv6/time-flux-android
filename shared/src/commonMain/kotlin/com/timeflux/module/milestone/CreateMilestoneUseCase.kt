package com.timeflux.module.milestone

import co.touchlab.kermit.Logger
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.Outcome
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.domain.model.map
import com.timeflux.domain.repository.TimelineRepository
import com.timeflux.util.Ulid
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

/**
 * Creates a new Milestone entry.
 *
 * Responsibilities:
 *  1. Validate the input — surface [Outcome.Failure.ValidationError] before touching the DB.
 *  2. Encode the [MilestonePayload] as a JSON string.
 *  3. Generate a time-ordered ULID anchored to [Params.createdAt].
 *  4. Delegate persistence to [TimelineRepository.insert].
 *  5. Return the new entry's ULID on success for post-create navigation.
 */
class CreateMilestoneUseCase(
    private val repository: TimelineRepository,
    private val json: Json,
) {

    private val log = Logger.withTag("CreateMilestone")

    /**
     * @param title       Required — the headline shown on the timeline card.
     * @param note        Optional short description visible below the title.
     * @param createdAt   When the milestone happened (defaults to now).
     * @param category    Broad grouping used for icon and color defaults.
     * @param emoji       Optional single emoji override for the card icon.
     * @param color       Optional hex accent color (e.g. "#E91E63").
     * @param locationName Human-readable place name (city, venue, etc.).
     * @param locationLat  Latitude for future map display.
     * @param locationLng  Longitude for future map display.
     * @param isPinned    Pin to top of timeline and include in decade highlights.
     */
    data class Params(
        val title: String,
        val note: String? = null,
        val createdAt: Instant = Clock.System.now(),
        val category: MilestoneCategory = MilestoneCategory.PERSONAL,
        val significance: MilestoneSignificance = MilestoneSignificance.NOTABLE,
        val emoji: String? = null,
        val color: String? = null,
        val locationName: String? = null,
        val locationLat: Double? = null,
        val locationLng: Double? = null,
        val isPinned: Boolean = false,
        val tags: List<String> = emptyList(),
        val isEnriched: Boolean = true,
        // Smart fields — populate based on category
        val company: String? = null,
        val role: String? = null,
        val institution: String? = null,
        val program: String? = null,
        val destination: String? = null,
        val people: String? = null,
        val whatChanged: String? = null,
    )

    suspend operator fun invoke(params: Params): Outcome<String> {
        log.d { "invoke title='${params.title}' category=${params.category.id}" }

        // ---- Validation ----
        if (params.title.isBlank()) {
            log.w { "validation failed: blank title" }
            return Outcome.Failure.ValidationError("Milestone title must not be blank.")
        }

        // ---- Build entry ----
        val id = Ulid.generate(params.createdAt.toEpochMilliseconds())
        val payloadJson = json.encodeToString(
            MilestonePayload.serializer(),
            MilestonePayload(
                category     = params.category,
                significance = params.significance,
                emoji        = params.emoji,
                color        = params.color,
                tags         = params.tags,
                isEnriched   = params.isEnriched,
                company      = params.company,
                role         = params.role,
                institution  = params.institution,
                program      = params.program,
                destination  = params.destination,
                people       = params.people,
                whatChanged  = params.whatChanged,
            ),
        )

        val entry = TimelineEntry(
            id           = id,
            createdAt    = params.createdAt,
            moduleType   = ModuleType.MILESTONE,
            title        = params.title,
            note         = params.note,
            locationName = params.locationName,
            locationLat  = params.locationLat,
            locationLng  = params.locationLng,
            isPinned     = params.isPinned,
            payload      = payloadJson,
            updatedAt    = params.createdAt,
        )

        val insertOutcome = repository.insert(entry)
        if (insertOutcome is Outcome.Failure) {
            log.e { "insert failed: $insertOutcome" }
            return insertOutcome
        }

        if (params.tags.isNotEmpty()) {
            val tagOutcome = repository.setTagsForEntry(id, params.tags)
            if (tagOutcome is Outcome.Failure) {
                log.e { "setTagsForEntry failed: $tagOutcome" }
                return tagOutcome
            }
        }

        log.i { "created milestone id=$id tags=${params.tags}" }
        return Outcome.Success(id)
    }
}
