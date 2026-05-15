package com.timeflux.android.ui

import androidx.compose.ui.graphics.Color
import com.timeflux.data.json.AppJson
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.module.milestone.MilestonePayload
import com.timeflux.module.milestone.MilestoneSignificance
import com.timeflux.module.mood.MoodPayload
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

// ---- Per-module UI metadata ----------------------------------------------------------------

fun ModuleType.accentColor(): Color = when (this) {
    ModuleType.MILESTONE -> Color(0xFF7C4DFF)
    ModuleType.MOOD      -> Color(0xFFFF6B9D)
    ModuleType.JOURNAL   -> Color(0xFF66BB6A)
    ModuleType.HABIT     -> Color(0xFFFFB300)
    ModuleType.SLEEP     -> Color(0xFF42A5F5)
    ModuleType.HEALTH    -> Color(0xFFEF5350)
    ModuleType.NOTE      -> Color(0xFF8D6E63)
    ModuleType.TASK      -> Color(0xFF26C6DA)
    ModuleType.PHOTO     -> Color(0xFFEC407A)
    ModuleType.GOAL      -> Color(0xFFFFA726)
}

fun ModuleType.defaultEmoji(): String = when (this) {
    ModuleType.MILESTONE -> "🏆"
    ModuleType.MOOD      -> "😊"
    ModuleType.JOURNAL   -> "📔"
    ModuleType.HABIT     -> "✅"
    ModuleType.SLEEP     -> "😴"
    ModuleType.HEALTH    -> "❤️"
    ModuleType.NOTE      -> "📝"
    ModuleType.TASK      -> "📌"
    ModuleType.PHOTO     -> "📷"
    ModuleType.GOAL      -> "🎯"
}

fun ModuleType.displayName(): String = when (this) {
    ModuleType.MILESTONE -> "Milestone"
    ModuleType.MOOD      -> "Mood"
    ModuleType.JOURNAL   -> "Journal"
    ModuleType.HABIT     -> "Habit"
    ModuleType.SLEEP     -> "Sleep"
    ModuleType.HEALTH    -> "Health"
    ModuleType.NOTE      -> "Note"
    ModuleType.TASK      -> "Task"
    ModuleType.PHOTO     -> "Photo"
    ModuleType.GOAL      -> "Goal"
}

// ---- Entry display helpers -----------------------------------------------------------------

/**
 * Returns the best emoji to show on this entry's card.
 * For mood: score-based emoji. For milestone: custom emoji from payload or default.
 */
fun TimelineEntry.cardEmoji(json: Json = AppJson): String = when (moduleType) {
    ModuleType.MOOD -> try {
        moodScoreEmoji(json.decodeFromString(MoodPayload.serializer(), payload).score)
    } catch (_: Exception) { moduleType.defaultEmoji() }

    ModuleType.MILESTONE -> try {
        json.decodeFromString(MilestonePayload.serializer(), payload).emoji
            ?: moduleType.defaultEmoji()
    } catch (_: Exception) { moduleType.defaultEmoji() }

    else -> moduleType.defaultEmoji()
}

/** Badge label shown on milestone timeline cards for MAJOR and DEFINING entries. */
fun MilestoneSignificance.badgeLabel(): String? = when (this) {
    MilestoneSignificance.MINOR    -> null
    MilestoneSignificance.NOTABLE  -> null
    MilestoneSignificance.MAJOR    -> "⭐ Major"
    MilestoneSignificance.DEFINING -> "🌟 Defining"
}

/** Accent color for the significance badge. */
fun MilestoneSignificance.badgeColor(): Color = when (this) {
    MilestoneSignificance.MINOR    -> Color(0xFF9E9E9E)
    MilestoneSignificance.NOTABLE  -> Color(0xFF7C4DFF)
    MilestoneSignificance.MAJOR    -> Color(0xFFFF8F00)
    MilestoneSignificance.DEFINING -> Color(0xFFFDD835)
}

/** Display label used in the form's significance chip row. */
fun MilestoneSignificance.displayLabel(): String = when (this) {
    MilestoneSignificance.MINOR    -> "Minor"
    MilestoneSignificance.NOTABLE  -> "Notable"
    MilestoneSignificance.MAJOR    -> "Major"
    MilestoneSignificance.DEFINING -> "Defining"
}

fun moodScoreEmoji(score: Int): String = when (score) {
    1    -> "😔"
    2    -> "😕"
    3    -> "😐"
    4    -> "🙂"
    else -> "😄"
}

// ---- Date formatting -----------------------------------------------------------------------

/**
 * Formats this instant as "MMM D, YYYY" in the current system timezone.
 * e.g. "May 14, 2026"
 */
fun Instant.toDisplayDate(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }
    return "$month ${local.dayOfMonth}, ${local.year}"
}
