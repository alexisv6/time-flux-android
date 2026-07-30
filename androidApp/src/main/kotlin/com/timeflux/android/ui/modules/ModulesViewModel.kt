package com.timeflux.android.ui.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timeflux.domain.model.ModuleType
import com.timeflux.module.ModuleRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin pass-through to [ModuleRegistry] — the invariants (enabling clears hidden, only disabled
 * modules can be hidden) live in the registry, so this cannot violate them and doesn't re-check.
 */
class ModulesViewModel(
    private val registry: ModuleRegistry,
) : ViewModel() {

    val state: StateFlow<ModulesUiState> =
        combine(registry.observeEnabled(), registry.observeHidden()) { enabled, hidden ->
            ModulesUiState(enabled = enabled, hidden = hidden, isLoaded = true)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ModulesUiState(),
        )

    fun setEnabled(type: ModuleType, enabled: Boolean) {
        viewModelScope.launch { registry.setEnabled(type, enabled) }
    }

    fun setHidden(type: ModuleType, hidden: Boolean) {
        viewModelScope.launch { registry.setHidden(type, hidden) }
    }

    /**
     * Marks first run done, then invokes [onComplete]. The callback runs after the write so
     * navigation can't outrun the flag and drop the user back into onboarding.
     */
    fun completeFirstRun(onComplete: () -> Unit) {
        viewModelScope.launch {
            registry.completeFirstRun()
            onComplete()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
