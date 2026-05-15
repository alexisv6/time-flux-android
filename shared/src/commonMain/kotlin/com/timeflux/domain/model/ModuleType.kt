package com.timeflux.domain.model

enum class ModuleType(val id: String) {
    MILESTONE("milestone"),
    MOOD("mood"),
    JOURNAL("journal"),
    HABIT("habit"),
    SLEEP("sleep"),
    HEALTH("health"),
    NOTE("note"),
    TASK("task"),
    PHOTO("photo"),
    GOAL("goal");

    companion object {
        private val byId = entries.associateBy { it.id }

        /** Returns the matching [ModuleType], falling back to [MILESTONE] for unknown ids
         *  (guards against future db rows written by a newer app version). */
        fun fromId(id: String): ModuleType = byId[id] ?: MILESTONE
    }
}
