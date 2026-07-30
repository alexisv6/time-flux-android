package com.timeflux.android.ui.timeline

import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.Tag
import com.timeflux.domain.model.TimelineEntry

/**
 * Immutable snapshot of everything the timeline screen needs to render.
 *
 * [isLoading]     — true during the initial page load (show full-screen spinner).
 * [isLoadingMore] — true when appending the next page (show footer spinner).
 * [hasMore]       — false when the last page returned fewer items than PAGE_SIZE.
 * [userMessage]   — one-shot message for the Snackbar; clear with [TimelineViewModel.messageShown].
 * [availableTags] — all tags in the DB, used to populate autocomplete in entry forms.
 * [enabledModules] — modules the user has switched on; drives which entry types can be created
 *                    and filtered. Entries from disabled modules stay on the timeline (spec 001).
 * [hiddenModules]  — disabled modules whose entries the user asked to hide. Excluded in the query,
 *                    never post-filtered, so paging stays correct.
 */
data class TimelineUiState(
    val entries: List<TimelineEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val userMessage: String? = null,
    val availableTags: List<Tag> = emptyList(),
    val enabledModules: Set<ModuleType> = emptySet(),
    val hiddenModules: Set<ModuleType> = emptySet(),
    val filter: TimelineFilter = TimelineFilter(),
    val selectedEntry: TimelineEntry? = null,
)
