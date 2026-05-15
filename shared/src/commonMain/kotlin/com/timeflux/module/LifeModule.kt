package com.timeflux.module

import com.timeflux.domain.model.ModuleType
import kotlinx.serialization.Serializable

@Serializable
data class LifeModule(
    val type: ModuleType,
    val isEnabled: Boolean,
    val displayName: String,
    val description: String,
)

val ALL_MODULES = listOf(
    LifeModule(ModuleType.MILESTONE, true,  "Milestones", "Significant life events and achievements"),
    LifeModule(ModuleType.MOOD,      true,  "Mood",       "Daily mood and emotion check-ins"),
    LifeModule(ModuleType.JOURNAL,   false, "Journal",    "Long-form entries with photos and notes"),
    LifeModule(ModuleType.HABIT,     false, "Habits",     "Recurring habits with streak tracking"),
    LifeModule(ModuleType.SLEEP,     false, "Sleep",      "Sleep logs and quality tracking"),
    LifeModule(ModuleType.HEALTH,    false, "Health",     "Body metrics and symptom tracking"),
    LifeModule(ModuleType.NOTE,      false, "Notes",      "Quick capture notes"),
    LifeModule(ModuleType.TASK,      false, "Tasks",      "Scheduled tasks and upcoming events"),
    LifeModule(ModuleType.PHOTO,     false, "Photos",     "Photo and memory entries"),
    LifeModule(ModuleType.GOAL,      false, "Goals",      "Long-term goals and milestones"),
)
