package com.timeflux.android.ui.timeline

import com.timeflux.data.json.AppJson
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.module.milestone.MilestonePayload
import com.timeflux.module.milestone.MilestoneSignificance
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class DateRangePreset(val label: String) {
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
}

data class TimelineFilter(
    val moduleType: ModuleType? = null,
    val dateRange: DateRangePreset? = null,
    val tags: List<String> = emptyList(),
    val tagsMatchAll: Boolean = false,
    val significantOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = moduleType != null || dateRange != null || tags.isNotEmpty() || significantOnly

    fun matches(entry: TimelineEntry): Boolean {
        if (moduleType != null && entry.moduleType != moduleType) return false

        if (dateRange != null) {
            if (entry.createdAt.toEpochMilliseconds() < dateRange.startEpochMs()) return false
        }

        if (tags.isNotEmpty()) {
            val entryTags = payloadTagNames(entry.payload)
            val matched = if (tagsMatchAll) tags.all { it in entryTags }
                          else tags.any { it in entryTags }
            if (!matched) return false
        }

        if (significantOnly) {
            if (entry.moduleType != ModuleType.MILESTONE) return false
            val sig = try {
                AppJson.decodeFromString(MilestonePayload.serializer(), entry.payload).significance
            } catch (_: Exception) { MilestoneSignificance.NOTABLE }
            if (sig != MilestoneSignificance.MAJOR && sig != MilestoneSignificance.DEFINING) return false
        }

        return true
    }
}

private fun payloadTagNames(payload: String): Set<String> = try {
    AppJson.parseToJsonElement(payload)
        .jsonObject["tags"]
        ?.jsonArray
        ?.map { it.jsonPrimitive.content }
        ?.toSet()
        ?: emptySet()
} catch (_: Exception) { emptySet() }

private fun DateRangePreset.startEpochMs(): Long {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(tz)
    val startDate: LocalDate = when (this) {
        DateRangePreset.THIS_WEEK  -> now.date.minus(now.dayOfWeek.value - 1, DateTimeUnit.DAY)
        DateRangePreset.THIS_MONTH -> LocalDate(now.year, now.monthNumber, 1)
        DateRangePreset.THIS_YEAR  -> LocalDate(now.year, 1, 1)
    }
    return LocalDateTime(startDate, LocalTime(0, 0, 0))
        .toInstant(tz)
        .toEpochMilliseconds()
}
