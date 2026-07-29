package com.timeflux.module

import com.timeflux.domain.model.ModuleType
import kotlinx.serialization.Serializable

/**
 * Static description of a module the app offers.
 *
 * This is the catalogue, not live state — whether a module is *currently* enabled comes from
 * [ModuleRegistry]. [enabledByDefault] only seeds that state on first run.
 *
 * @param isAvailable      false for modules that are designed but not implemented yet. Unavailable
 *                         modules cannot be enabled or hidden; the picker names them in a footer
 *                         rather than offering a switch that leads nowhere.
 * @param enabledByDefault the value [ModuleRegistry] falls back to when the user has never toggled
 *                         this module.
 */
@Serializable
data class LifeModule(
    val type: ModuleType,
    val isAvailable: Boolean,
    val enabledByDefault: Boolean,
    val displayName: String,
    val description: String,
)

/** Every module, in the product's priority order (see docs/architecture-and-modules.md). */
val ALL_MODULES = listOf(
    LifeModule(ModuleType.MILESTONE, true,  true,  "Milestones", "Significant life events and achievements"),
    LifeModule(ModuleType.MOOD,      true,  true,  "Mood",       "Daily mood and emotion check-ins"),
    LifeModule(ModuleType.JOURNAL,   false, false, "Journal",    "Long-form entries with photos and notes"),
    LifeModule(ModuleType.HABIT,     false, false, "Habits",     "Recurring habits with streak tracking"),
    LifeModule(ModuleType.SLEEP,     false, false, "Sleep",      "Sleep logs and quality tracking"),
    LifeModule(ModuleType.HEALTH,    false, false, "Health",     "Body metrics and symptom tracking"),
    LifeModule(ModuleType.NOTE,      false, false, "Notes",      "Quick capture notes"),
    LifeModule(ModuleType.TASK,      false, false, "Tasks",      "Scheduled tasks and upcoming events"),
    LifeModule(ModuleType.PHOTO,     false, false, "Photos",     "Photo and memory entries"),
    LifeModule(ModuleType.GOAL,      false, false, "Goals",      "Long-term goals and milestones"),
)

/** Modules the user can actually turn on today. */
val AVAILABLE_MODULES: List<LifeModule> = ALL_MODULES.filter { it.isAvailable }

/** Modules that exist in the roadmap but ship no UI yet — used for the picker's footer. */
val UPCOMING_MODULES: List<LifeModule> = ALL_MODULES.filter { !it.isAvailable }
