package com.timeflux.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.touchlab.kermit.Logger
import com.timeflux.data.db.mapToTimelineEntry
import com.timeflux.data.db.toMonthDay
import com.timeflux.data.db.toYearMonth
import com.timeflux.data.db.toYearWeek
import com.timeflux.db.TimeFluxDatabase
import com.timeflux.domain.model.Outcome
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.domain.model.YearGridRow
import com.timeflux.domain.model.toOutcome
import com.timeflux.domain.repository.TimelineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Local SQLite implementation of [TimelineRepository].
 *
 * Threading: all blocking DB calls are dispatched on [Dispatchers.IO] so callers on the
 * main thread are never blocked. Reactive [Flow] queries use [Dispatchers.IO] for the
 * initial fetch and re-emit on table change notifications from SQLDelight.
 *
 * [timeZone] defaults to the current system timezone and is used to compute the
 * [month_day]/[year_month]/[year_week] index columns on write.
 */
class TimelineRepositoryImpl(
    db: TimeFluxDatabase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : TimelineRepository {

    private val log = Logger.withTag("TimelineRepo")
    private val q = db.timeFluxQueries

    // ---- Reactive reads ----------------------------------------------------------------

    override fun observeOnThisDay(monthDay: String): Flow<Outcome<List<TimelineEntry>>> =
        q.selectOnThisDay(month_day = monthDay, mapper = ::mapListRow)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map<List<TimelineEntry>, Outcome<List<TimelineEntry>>> { Outcome.Success(it) }
            .catch { e ->
                log.e(e) { "observeOnThisDay month_day=$monthDay" }
                emit(Outcome.Failure.DatabaseError(e))
            }

    override fun observeYearGrid(year: String): Flow<Outcome<List<YearGridRow>>> =
        q.selectYearGrid(year = year) { year_month, module_type, count ->
            YearGridRow(yearMonth = year_month, moduleType = module_type, count = count)
        }
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map<List<YearGridRow>, Outcome<List<YearGridRow>>> { Outcome.Success(it) }
            .catch { e ->
                log.e(e) { "observeYearGrid year=$year" }
                emit(Outcome.Failure.DatabaseError(e))
            }

    // ---- Paginated one-shot reads -------------------------------------------------------

    override suspend fun getPageBefore(
        beforeTs: Long,
        beforeId: String,
        limit: Long,
    ): Outcome<List<TimelineEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectPageBefore(
                before_ts = beforeTs,
                before_id = beforeId,
                limit = limit,
                mapper = ::mapListRow,
            ).executeAsList()
        }.toOutcome()
    }

    override suspend fun getPageAfter(
        afterTs: Long,
        afterId: String,
        limit: Long,
    ): Outcome<List<TimelineEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectPageAfter(
                after_ts = afterTs,
                after_id = afterId,
                limit = limit,
                mapper = ::mapListRow,
            ).executeAsList()
        }.toOutcome()
    }

    override suspend fun getPageByModule(
        moduleType: String,
        beforeTs: Long,
        beforeId: String,
        limit: Long,
    ): Outcome<List<TimelineEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectPageByModule(
                module_type = moduleType,
                before_ts = beforeTs,
                before_id = beforeId,
                limit = limit,
                mapper = ::mapListRow,
            ).executeAsList()
        }.toOutcome()
    }

    override suspend fun getById(id: String): Outcome<TimelineEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.selectById(id) { id_, created_at, ends_at, module_type, title, note, media_uri,
                    location_lat, location_lng, location_name, is_pinned, payload,
                    deleted_at, updated_at ->
                    mapToTimelineEntry(
                        id = id_, createdAt = created_at, endsAt = ends_at,
                        moduleType = module_type, title = title, note = note,
                        mediaUri = media_uri, locationLat = location_lat,
                        locationLng = location_lng, locationName = location_name,
                        isPinned = is_pinned, payload = payload,
                        deletedAt = deleted_at, updatedAt = updated_at,
                    )
                }.executeAsOneOrNull()
            }.fold(
                onSuccess = { entry ->
                    if (entry != null) Outcome.Success(entry)
                    else {
                        log.w { "getById not found id=$id" }
                        Outcome.Failure.NotFound(id)
                    }
                },
                onFailure = { e ->
                    log.e(e) { "getById id=$id" }
                    Outcome.Failure.DatabaseError(e)
                },
            )
        }

    // ---- Search ------------------------------------------------------------------------

    override suspend fun search(
        query: String,
        moduleType: String?,
        limit: Long,
    ): Outcome<List<TimelineEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            // Search results carry only id/createdAt/moduleType/title/note.
            // Callers should call getById on tap to retrieve the full entry.
            q.searchEntries(
                query = query,
                module_type = moduleType,
                limit = limit,
            ) { id, created_at, module_type_, title, note ->
                mapToTimelineEntry(
                    id = id, createdAt = created_at, endsAt = null,
                    moduleType = module_type_, title = title, note = note,
                    mediaUri = null, locationName = null,
                    isPinned = 0L, payload = "{}",
                    updatedAt = created_at,
                )
            }.executeAsList()
        }.toOutcome()
    }

    // ---- Writes ------------------------------------------------------------------------

    override suspend fun insert(entry: TimelineEntry): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val createdAt = entry.createdAt
                q.insertEntry(
                    id            = entry.id,
                    created_at    = createdAt.toEpochMilliseconds(),
                    ends_at       = entry.endsAt?.toEpochMilliseconds(),
                    module_type   = entry.moduleType.id,
                    title         = entry.title,
                    note          = entry.note,
                    media_uri     = entry.mediaUri,
                    location_lat  = entry.locationLat,
                    location_lng  = entry.locationLng,
                    location_name = entry.locationName,
                    is_pinned     = if (entry.isPinned) 1L else 0L,
                    payload       = entry.payload,
                    updated_at    = entry.updatedAt.toEpochMilliseconds(),
                    month_day     = createdAt.toMonthDay(timeZone),
                    year_month    = createdAt.toYearMonth(timeZone),
                    year_week     = createdAt.toYearWeek(timeZone),
                )
                log.d { "insert id=${entry.id} module=${entry.moduleType.id}" }
            }.toOutcome()
        }

    override suspend fun update(entry: TimelineEntry): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.updateEntry(
                    title      = entry.title,
                    note       = entry.note,
                    media_uri  = entry.mediaUri,
                    is_pinned  = if (entry.isPinned) 1L else 0L,
                    payload    = entry.payload,
                    updated_at = Clock.System.now().toEpochMilliseconds(),
                    id         = entry.id,
                )
                log.d { "update id=${entry.id}" }
            }.toOutcome()
        }

    override suspend fun softDelete(id: String): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                q.softDeleteEntry(deleted_at = now, updated_at = now, id = id)
                log.d { "softDelete id=$id" }
            }.toOutcome()
        }

    override suspend fun restore(id: String): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.restoreEntry(updated_at = Clock.System.now().toEpochMilliseconds(), id = id)
                log.d { "restore id=$id" }
            }.toOutcome()
        }

    // ---- Private helpers ---------------------------------------------------------------

    /** Lightweight row mapper for all paginated list queries (no lat/lng/deletedAt). */
    private fun mapListRow(
        id: String, created_at: Long, ends_at: Long?, module_type: String,
        title: String?, note: String?, media_uri: String?, location_name: String?,
        is_pinned: Long, payload: String, updated_at: Long,
    ): TimelineEntry = mapToTimelineEntry(
        id = id, createdAt = created_at, endsAt = ends_at,
        moduleType = module_type, title = title, note = note,
        mediaUri = media_uri, locationName = location_name,
        isPinned = is_pinned, payload = payload, updatedAt = updated_at,
    )
}
