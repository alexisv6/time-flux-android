package com.timeflux.module.mood

import co.touchlab.kermit.Logger
import com.timeflux.domain.model.Outcome
import com.timeflux.domain.repository.TimelineRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class UpdateMoodEntryUseCase(
    private val repository: TimelineRepository,
    private val json: Json,
) {

    private val log = Logger.withTag("UpdateMoodEntry")

    data class Params(
        val id: String,
        val score: Int,
        val note: String? = null,
        val energy: Int? = null,
        val emotion: String? = null,
        val emotions: List<String> = emptyList(),
        val factors: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val isEnriched: Boolean = true,
    )

    suspend operator fun invoke(params: Params): Outcome<Unit> {
        log.d { "invoke id=${params.id} score=${params.score}" }

        if (params.score !in CreateMoodEntryUseCase.SCORE_RANGE) {
            return Outcome.Failure.ValidationError(
                "Mood score must be between ${CreateMoodEntryUseCase.SCORE_RANGE.first} and ${CreateMoodEntryUseCase.SCORE_RANGE.last}, got ${params.score}."
            )
        }
        params.energy?.let { energy ->
            if (energy !in CreateMoodEntryUseCase.SCORE_RANGE) {
                return Outcome.Failure.ValidationError(
                    "Energy score must be between ${CreateMoodEntryUseCase.SCORE_RANGE.first} and ${CreateMoodEntryUseCase.SCORE_RANGE.last}, got $energy."
                )
            }
        }

        val existing = repository.getById(params.id)
        if (existing is Outcome.Failure) {
            log.e { "getById failed: $existing" }
            return existing
        }
        val entry = (existing as Outcome.Success).data

        val newPayload = json.encodeToString(
            MoodPayload.serializer(),
            MoodPayload(
                score      = params.score,
                energy     = params.energy,
                emotion    = params.emotion,
                emotions   = params.emotions,
                factors    = params.factors,
                tags       = params.tags,
                isEnriched = params.isEnriched,
            ),
        )

        val updated = entry.copy(
            note      = params.note?.trim()?.ifBlank { null },
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

        log.i { "updated mood entry id=${params.id}" }
        return Outcome.Success(Unit)
    }
}
