package com.timeflux.module.mood

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
 * Creates a new Mood check-in entry.
 *
 * Responsibilities:
 *  1. Validate score and energy are within the 1–5 range.
 *  2. Encode the [MoodPayload] as a JSON string.
 *  3. Generate a time-ordered ULID anchored to [Params.createdAt].
 *  4. Delegate persistence to [TimelineRepository.insert].
 *  5. Return the new entry's ULID on success.
 *
 * Mood entries intentionally have no required title — the score + optional note
 * is sufficient for timeline display.
 */
class CreateMoodEntryUseCase(
    private val repository: TimelineRepository,
    private val json: Json,
) {

    private val log = Logger.withTag("CreateMoodEntry")

    /**
     * @param score    Overall mood 1–5 (required).
     * @param note     Optional free-text context visible on the timeline card.
     * @param energy   Optional energy level 1–5.
     * @param emotion  Optional free-form tag (e.g. "anxious", "grateful").
     * @param createdAt When the check-in happened (defaults to now).
     */
    data class Params(
        val score: Int,
        val note: String? = null,
        val energy: Int? = null,
        val emotion: String? = null,
        val createdAt: Instant = Clock.System.now(),
    )

    suspend operator fun invoke(params: Params): Outcome<String> {
        log.d { "invoke score=${params.score} energy=${params.energy}" }

        // ---- Validation ----
        if (params.score !in SCORE_RANGE) {
            log.w { "validation failed: score=${params.score} out of $SCORE_RANGE" }
            return Outcome.Failure.ValidationError(
                "Mood score must be between ${SCORE_RANGE.first} and ${SCORE_RANGE.last}, got ${params.score}."
            )
        }
        params.energy?.let { energy ->
            if (energy !in SCORE_RANGE) {
                log.w { "validation failed: energy=$energy out of $SCORE_RANGE" }
                return Outcome.Failure.ValidationError(
                    "Energy score must be between ${SCORE_RANGE.first} and ${SCORE_RANGE.last}, got $energy."
                )
            }
        }

        // ---- Build entry ----
        val id = Ulid.generate(params.createdAt.toEpochMilliseconds())
        val payloadJson = json.encodeToString(
            MoodPayload.serializer(),
            MoodPayload(
                score   = params.score,
                energy  = params.energy,
                emotion = params.emotion,
            ),
        )

        val entry = TimelineEntry(
            id         = id,
            createdAt  = params.createdAt,
            moduleType = ModuleType.MOOD,
            note       = params.note,
            payload    = payloadJson,
            updatedAt  = params.createdAt,
        )

        return repository.insert(entry)
            .map { id }
            .also { outcome ->
                when (outcome) {
                    is Outcome.Success -> log.i { "created mood entry id=$id score=${params.score}" }
                    is Outcome.Failure -> log.e { "insert failed: $outcome" }
                }
            }
    }

    companion object {
        val SCORE_RANGE = 1..5
    }
}
