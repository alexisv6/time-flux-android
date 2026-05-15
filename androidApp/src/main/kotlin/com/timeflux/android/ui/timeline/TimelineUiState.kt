package com.timeflux.android.ui.timeline

import com.timeflux.domain.model.TimelineEntry

/**
 * Immutable snapshot of everything the timeline screen needs to render.
 *
 * [isLoading]     — true during the initial page load (show full-screen spinner).
 * [isLoadingMore] — true when appending the next page (show footer spinner).
 * [hasMore]       — false when the last page returned fewer items than PAGE_SIZE.
 * [userMessage]   — one-shot message for the Snackbar; clear with [TimelineViewModel.messageShown].
 */
data class TimelineUiState(
    val entries: List<TimelineEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val userMessage: String? = null,
)
