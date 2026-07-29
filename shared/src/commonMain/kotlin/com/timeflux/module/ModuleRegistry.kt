package com.timeflux.module

import com.timeflux.domain.model.ModuleType
import kotlinx.coroutines.flow.Flow

/**
 * Which modules the user has turned on, and which disabled modules have had their entries hidden
 * from the timeline.
 *
 * This is user preference, not timeline data — it deliberately lives outside the database
 * (data-model principle 1). See docs/specs/001-module-registry-and-navigation.md.
 *
 * Contract:
 *  - Reads that need live-update semantics return [Flow]; one-shot reads and all writes are `suspend`.
 *  - Modules with [LifeModule.isAvailable] false can never be enabled or hidden — writes for them
 *    are silently ignored rather than throwing, so UI code needs no special-casing.
 *
 * Invariants enforced by the implementation, not by callers:
 *  - **Enabling a module clears its hidden flag.** "Enabled but hidden" would let the user create
 *    entries that never appear.
 *  - **Only a disabled module can be hidden.** [setHidden] on an enabled module is a no-op.
 */
interface ModuleRegistry {

    /** Modules currently switched on. Re-emits on every change. Never contains unavailable modules. */
    fun observeEnabled(): Flow<Set<ModuleType>>

    /**
     * Disabled modules whose existing entries the user has asked to hide from the timeline.
     * Hiding removes entries from view only — rows are never modified or deleted.
     */
    fun observeHidden(): Flow<Set<ModuleType>>

    /** One-shot read of [observeEnabled]. */
    suspend fun isEnabled(type: ModuleType): Boolean

    /**
     * Turn a module on or off. Enabling also clears the module's hidden flag.
     * No-op for unavailable modules.
     */
    suspend fun setEnabled(type: ModuleType, enabled: Boolean)

    /**
     * Hide or show a disabled module's existing entries.
     * No-op for unavailable modules and for modules that are currently enabled.
     */
    suspend fun setHidden(type: ModuleType, hidden: Boolean)

    /** False until the user has been through the first-run module picker. */
    suspend fun isFirstRunComplete(): Boolean

    /** Marks first run done so subsequent launches open straight onto the timeline. */
    suspend fun completeFirstRun()
}
