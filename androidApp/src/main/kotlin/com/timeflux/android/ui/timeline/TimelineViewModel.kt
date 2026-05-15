package com.timeflux.android.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.timeflux.domain.model.Outcome
import com.timeflux.domain.repository.TimelineRepository
import com.timeflux.module.milestone.CreateMilestoneUseCase
import com.timeflux.module.mood.CreateMoodEntryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val createMilestone: CreateMilestoneUseCase,
    private val createMoodEntry: CreateMoodEntryUseCase,
) : ViewModel() {

    private val log = Logger.withTag("TimelineVM")

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    /**
     * Emits [Unit] once after each successful entry creation so the UI can dismiss the
     * add-entry sheet without the ViewModel needing to know the sheet exists.
     */
    private val _entryAdded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val entryAdded: SharedFlow<Unit> = _entryAdded.asSharedFlow()

    init {
        loadInitialPage()
        loadAllTags()
    }

    // ---- Public actions ----------------------------------------------------------------

    fun refresh() {
        loadInitialPage()
        loadAllTags()
    }

    /**
     * Appends the next page when the user scrolls near the bottom. Guards against
     * concurrent calls and against loading past the last page.
     */
    fun loadNextPage() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore || current.entries.isEmpty()) return

        val last = current.entries.last()
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            when (val result = repository.getPageBefore(
                beforeTs = last.createdAt.toEpochMilliseconds(),
                beforeId = last.id,
            )) {
                is Outcome.Success -> _state.update { s ->
                    s.copy(
                        entries = s.entries + result.data,
                        isLoadingMore = false,
                        hasMore = result.data.size >= TimelineRepository.PAGE_SIZE.toInt(),
                    )
                }
                is Outcome.Failure -> {
                    log.e { "loadNextPage failed: $result" }
                    _state.update { it.copy(isLoadingMore = false, userMessage = "Failed to load more entries.") }
                }
            }
        }
    }

    fun addMilestone(params: CreateMilestoneUseCase.Params) {
        viewModelScope.launch {
            when (val result = createMilestone(params)) {
                is Outcome.Success                -> prependNewEntry(result.data)
                is Outcome.Failure.ValidationError -> _state.update { it.copy(userMessage = result.message) }
                is Outcome.Failure                -> _state.update { it.copy(userMessage = "Failed to save milestone.") }
            }
        }
    }

    fun addMoodEntry(params: CreateMoodEntryUseCase.Params) {
        viewModelScope.launch {
            when (val result = createMoodEntry(params)) {
                is Outcome.Success                -> prependNewEntry(result.data)
                is Outcome.Failure.ValidationError -> _state.update { it.copy(userMessage = result.message) }
                is Outcome.Failure                -> _state.update { it.copy(userMessage = "Failed to save mood entry.") }
            }
        }
    }

    /** Call after the Snackbar has shown [TimelineUiState.userMessage] to clear it. */
    fun messageShown() = _state.update { it.copy(userMessage = null) }

    // ---- Private helpers ---------------------------------------------------------------

    private fun loadAllTags() {
        viewModelScope.launch {
            when (val result = repository.getAllTags()) {
                is Outcome.Success -> _state.update { it.copy(availableTags = result.data) }
                is Outcome.Failure -> log.w { "loadAllTags failed: $result" }
            }
        }
    }

    private fun loadInitialPage() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, userMessage = null) }
            when (val result = repository.getPageBefore(
                // Long.MAX_VALUE / empty string: selects all entries (every created_at is < MAX)
                beforeTs = Long.MAX_VALUE,
                beforeId = "",
            )) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        entries = result.data,
                        isLoading = false,
                        hasMore = result.data.size >= TimelineRepository.PAGE_SIZE.toInt(),
                    )
                }
                is Outcome.Failure -> {
                    log.e { "loadInitialPage failed: $result" }
                    _state.update { it.copy(isLoading = false, userMessage = "Failed to load timeline.") }
                }
            }
        }
    }

    /**
     * Fetches the newly created entry by id and prepends it to the list, then fires
     * [entryAdded] so the sheet dismisses. Falls back to a full page reload if the
     * fetch fails (e.g. DB race).
     */
    private suspend fun prependNewEntry(id: String) {
        loadAllTags()
        when (val result = repository.getById(id)) {
            is Outcome.Success -> {
                _state.update { it.copy(entries = listOf(result.data) + it.entries) }
                _entryAdded.emit(Unit)
            }
            is Outcome.Failure -> {
                log.w { "prependNewEntry getById failed for $id, falling back to reload" }
                loadInitialPage()
                _entryAdded.emit(Unit)
            }
        }
    }
}
