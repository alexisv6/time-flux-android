package com.timeflux.android.ui.modules

import com.timeflux.domain.model.ModuleType

/**
 * Immutable snapshot of the module picker.
 *
 * [isLoaded] is false only for the first frame, before the registry's flows have emitted. The
 * screen renders no rows until then — a switch briefly showing the wrong position reads as a bug.
 */
data class ModulesUiState(
    val enabled: Set<ModuleType> = emptySet(),
    val hidden: Set<ModuleType> = emptySet(),
    val isLoaded: Boolean = false,
)
