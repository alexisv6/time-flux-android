package com.timeflux.data.db

import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.TimelineEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ---- Time-field helpers ----------------------------------------------------------------
// These must produce output identical to the SQLite strftime() expressions used in the
// schema (see TimeFlux.sq) so that WHERE/index lookups on month_day, year_month, year_week
// correctly match values written by the repository.

/**
 * Formats this instant as "MM-DD" in [tz].
 * Equivalent to SQLite: `strftime('%m-%d', created_at / 1000, 'unixepoch')`.
 */
internal fun Instant.toMonthDay(tz: TimeZone): String {
    val local = toLocalDateTime(tz)
    return buildString {
        append(local.monthNumber.toString().padStart(2, '0'))
        append('-')
        append(local.dayOfMonth.toString().padStart(2, '0'))
    }
}

/**
 * Formats this instant as "YYYY-MM" in [tz].
 * Equivalent to SQLite: `strftime('%Y-%m', created_at / 1000, 'unixepoch')`.
 */
internal fun Instant.toYearMonth(tz: TimeZone): String {
    val local = toLocalDateTime(tz)
    return buildString {
        append(local.year)
        append('-')
        append(local.monthNumber.toString().padStart(2, '0'))
    }
}

/**
 * Formats this instant as "YYYY-WW" in [tz] where WW is the week number (00–53,
 * Monday = first day of week).
 * Equivalent to SQLite: `strftime('%Y-%W', created_at / 1000, 'unixepoch')`.
 */
internal fun Instant.toYearWeek(tz: TimeZone): String {
    val date = toLocalDateTime(tz).date
    val week = date.sqliteWeekOfYear()
    return "${date.year}-${week.toString().padStart(2, '0')}"
}

/**
 * SQLite `%W` week-of-year: Monday is the first day, week 00 contains any days that
 * fall before the year's first Monday.
 */
private fun LocalDate.sqliteWeekOfYear(): Int {
    val dayOfYear = dayOfYear - 1                                    // 0-indexed
    val jan1DayOfWeek = LocalDate(year, 1, 1).dayOfWeek.value        // 1=Mon … 7=Sun
    val daysBeforeFirstMonday = if (jan1DayOfWeek == 1) 0 else (8 - jan1DayOfWeek) % 7
    return if (dayOfYear < daysBeforeFirstMonday) 0
    else (dayOfYear - daysBeforeFirstMonday) / 7 + 1
}

// ---- Row → domain mapper ---------------------------------------------------------------

/**
 * Converts raw SQLite column values into a [TimelineEntry] domain object.
 *
 * Fields absent from lightweight list queries ([locationLat], [locationLng], [deletedAt])
 * default to `null` — callers that need those fields should use [selectById].
 */
internal fun mapToTimelineEntry(
    id: String,
    createdAt: Long,
    endsAt: Long?,
    moduleType: String,
    title: String?,
    note: String?,
    mediaUri: String?,
    locationLat: Double? = null,
    locationLng: Double? = null,
    locationName: String?,
    isPinned: Long,
    payload: String,
    deletedAt: Long? = null,
    updatedAt: Long,
): TimelineEntry = TimelineEntry(
    id           = id,
    createdAt    = Instant.fromEpochMilliseconds(createdAt),
    endsAt       = endsAt?.let(Instant::fromEpochMilliseconds),
    moduleType   = ModuleType.fromId(moduleType),
    title        = title,
    note         = note,
    mediaUri     = mediaUri,
    locationLat  = locationLat,
    locationLng  = locationLng,
    locationName = locationName,
    isPinned     = isPinned != 0L,
    payload      = payload,
    deletedAt    = deletedAt?.let(Instant::fromEpochMilliseconds),
    updatedAt    = Instant.fromEpochMilliseconds(updatedAt),
)
