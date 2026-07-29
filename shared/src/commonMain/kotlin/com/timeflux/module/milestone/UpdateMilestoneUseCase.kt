package com.timeflux.module.milestone

import co.touchlab.kermit.Logger
import com.timeflux.domain.model.Outcome
import com.timeflux.domain.repository.TimelineRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class UpdateMilestoneUseCase(
    private val repository: TimelineRepository,
    private val json: Json,
) {

    private val log = Logger.withTag("UpdateMilestone")

    data class Params(
        val id: String,
        val title: String,
        val note: String? = null,
        val category: MilestoneCategory = MilestoneCategory.PERSONAL,
        val significance: MilestoneSignificance = MilestoneSignificance.NOTABLE,
        val emoji: String? = null,
        val isPinned: Boolean = false,
        val tags: List<String> = emptyList(),
        val isEnriched: Boolean = true,
        val company: String? = null,
        val role: String? = null,
        val institution: String? = null,
        val program: String? = null,
        val destination: String? = null,
        val people: String? = null,
        val whatChanged: String? = null,
    )

    suspend operator fun invoke(params: Params): Outcome<Unit> {
        log.d { "invoke id=${params.id} title='${params.title}'" }

        if (params.title.isBlank()) {
            return Outcome.Failure.ValidationError("Milestone title must not be blank.")
        }

        val existing = repository.getById(params.id)
        if (existing is Outcome.Failure) {
            log.e { "getById failed: $existing" }
            return existing
        }
        val entry = (existing as Outcome.Success).data

        val newPayload = json.encodeToString(
            MilestonePayload.serializer(),
            MilestonePayload(
                category     = params.category,
                significance = params.significance,
                emoji        = params.emoji,
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

        val updated = entry.copy(
            title     = params.title.trim(),
            note      = params.note?.trim()?.ifBlank { null },
            isPinned  = params.isPinned,
            payload   = newPayload,
            updatedAt = Clock.System.now(),
        )

        val updateResult = repository.update(updated)
        if (updateResult is Outcome.Failure) {
            log.e { "update failed: $updateResult" }
            return updateResult
        }

        repository.deleteAllTagsForEntry(params.id)
        if (params.tags.isNotEmpty()) {
            val tagResult = repository.setTagsForEntry(params.id, params.tags)
            if (tagResult is Outcome.Failure) {
                log.e { "setTagsForEntry failed: $tagResult" }
                return tagResult
            }
        }

        log.i { "updated milestone id=${params.id}" }
        return Outcome.Success(Unit)
    }
}
