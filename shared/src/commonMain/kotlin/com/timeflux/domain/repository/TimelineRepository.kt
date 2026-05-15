package com.timeflux.domain.repository

import com.timeflux.domain.model.Outcome
import com.timeflux.domain.model.Tag
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.domain.model.YearGridRow
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for timeline data.
 *
 * Contract:
 *  - Reads that need live-update semantics return [Flow]; one-shot reads and all writes are `suspend`.
 *  - Every method returns [Outcome] — callers handle errors exhaustively via `when`.
 *  - The v2 sync implementation will replace this binding in the DI module; callers are unaffected.
 *
 * Threading: implementations are responsible for dispatching blocking work off the main thread.
 * Callers may call from any coroutine scope.
 */
interface TimelineRepository {

    // ---- Reactive reads (Flow re-emits whenever the underlying table changes) ----

    /**
     * All active entries whose `month_day` column matches [monthDay] (format "MM-DD"),
     * spanning every year. Used for the "On This Day" feature.
     */
    fun observeOnThisDay(monthDay: String): Flow<Outcome<List<TimelineEntry>>>

    /**
     * Year-over-year heatmap data: entry count per (year_month, module_type) for the given
     * 4-digit [year] string (e.g. "2025"). Used by the year calendar view.
     */
    fun observeYearGrid(year: String): Flow<Outcome<List<YearGridRow>>>

    // ---- Paginated one-shot reads ----

    /** Page of entries older than the cursor ([beforeTs] epoch-ms, [beforeId] ULID). */
    suspend fun getPageBefore(
        beforeTs: Long,
        beforeId: String,
        limit: Long = PAGE_SIZE,
    ): Outcome<List<TimelineEntry>>

    /** Page of entries newer than the cursor ([afterTs] epoch-ms, [afterId] ULID). */
    suspend fun getPageAfter(
        afterTs: Long,
        afterId: String,
        limit: Long = PAGE_SIZE,
    ): Outcome<List<TimelineEntry>>

    /** Page filtered to a single module, older than the cursor. */
    suspend fun getPageByModule(
        moduleType: String,
        beforeTs: Long,
        beforeId: String,
        limit: Long = PAGE_SIZE,
    ): Outcome<List<TimelineEntry>>

    /**
     * Fetch a single entry by id, including all fields (lat/lng, deleted_at).
     * Returns [Outcome.Failure.NotFound] if the id does not exist.
     */
    suspend fun getById(id: String): Outcome<TimelineEntry>

    // ---- Search ----

    /**
     * Full-text search across title, note, and journal body text.
     * [moduleType] optionally restricts results to a single module (pass `null` for all).
     * Results carry only the columns returned by the search query (id, createdAt, moduleType,
     * title, note); callers should call [getById] to fetch the full entry on tap.
     */
    suspend fun search(
        query: String,
        moduleType: String? = null,
        limit: Long = SEARCH_LIMIT,
    ): Outcome<List<TimelineEntry>>

    // ---- Tags ----

    /** All tags in the database, sorted alphabetically. Used to populate autocomplete. */
    suspend fun getAllTags(): Outcome<List<Tag>>

    /**
     * Atomically link [tagNames] to [entryId], creating any tags that don't yet exist.
     * Existing links for this entry are not removed — call [deleteAllTagsForEntry] first
     * if replacing the full tag set (e.g. on edit).
     */
    suspend fun setTagsForEntry(entryId: String, tagNames: List<String>): Outcome<Unit>

    /** Remove all tag links for [entryId] (used before re-saving tags on edit). */
    suspend fun deleteAllTagsForEntry(entryId: String): Outcome<Unit>

    // ---- Writes ----

    /**
     * Persist a new entry. The caller must supply a valid ULID for [TimelineEntry.id] —
     * use [com.timeflux.util.Ulid.generate] for this.
     */
    suspend fun insert(entry: TimelineEntry): Outcome<Unit>

    /** Update mutable fields (title, note, mediaUri, isPinned, payload) on an existing entry. */
    suspend fun update(entry: TimelineEntry): Outcome<Unit>

    /** Soft-delete — row is retained with a `deleted_at` timestamp for eventual sync tombstone. */
    suspend fun softDelete(id: String): Outcome<Unit>

    /** Undo a soft-delete, clearing `deleted_at`. */
    suspend fun restore(id: String): Outcome<Unit>

    companion object {
        const val PAGE_SIZE = 50L
        const val SEARCH_LIMIT = 30L
    }
}
