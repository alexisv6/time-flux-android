package com.timeflux.module

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.timeflux.domain.model.ModuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * [ModuleRegistry] backed by platform key-value storage (SharedPreferences on Android,
 * NSUserDefaults on iOS) via multiplatform-settings.
 *
 * Keys are built from [ModuleType.id] strings, so shipping a new module is a data change in
 * [ALL_MODULES] — never a migration (data-model principle 4).
 */
@OptIn(ExperimentalSettingsApi::class)
class SettingsModuleRegistry(
    private val settings: ObservableSettings,
) : ModuleRegistry {

    override fun observeEnabled(): Flow<Set<ModuleType>> =
        observeFlags(KEY_ENABLED) { it.enabledByDefault }

    override fun observeHidden(): Flow<Set<ModuleType>> =
        observeFlags(KEY_HIDDEN) { false }

    override suspend fun isEnabled(type: ModuleType): Boolean {
        val module = availableModule(type) ?: return false
        return settings.getBoolean(KEY_ENABLED + type.id, module.enabledByDefault)
    }

    override suspend fun setEnabled(type: ModuleType, enabled: Boolean) {
        availableModule(type) ?: return
        settings.putBoolean(KEY_ENABLED + type.id, enabled)
        // A module you can log entries in must never have those entries hidden (spec D4).
        if (enabled) settings.putBoolean(KEY_HIDDEN + type.id, false)
    }

    override suspend fun setHidden(type: ModuleType, hidden: Boolean) {
        availableModule(type) ?: return
        // Hiding is the escape hatch for an abandoned module, so it only applies once disabled.
        if (hidden && isEnabled(type)) return
        settings.putBoolean(KEY_HIDDEN + type.id, hidden)
    }

    override suspend fun isFirstRunComplete(): Boolean =
        settings.getBoolean(KEY_FIRST_RUN_COMPLETE, false)

    override suspend fun completeFirstRun() {
        settings.putBoolean(KEY_FIRST_RUN_COMPLETE, true)
    }

    // ---- Private helpers ---------------------------------------------------------------

    private fun availableModule(type: ModuleType): LifeModule? =
        AVAILABLE_MODULES.firstOrNull { it.type == type }

    /**
     * Combines one boolean flow per available module into a single set of the modules whose flag
     * is true. Unavailable modules are never observed — their keys are never written.
     */
    private fun observeFlags(
        keyPrefix: String,
        default: (LifeModule) -> Boolean,
    ): Flow<Set<ModuleType>> {
        if (AVAILABLE_MODULES.isEmpty()) return flowOf(emptySet())
        val flows = AVAILABLE_MODULES.map { module ->
            settings.getBooleanFlow(keyPrefix + module.type.id, default(module))
        }
        return combine(flows) { values ->
            AVAILABLE_MODULES
                .filterIndexed { index, _ -> values[index] }
                .map { it.type }
                .toSet()
        }.distinctUntilChanged()
    }

    private companion object {
        const val KEY_ENABLED = "module.enabled."
        const val KEY_HIDDEN = "module.hidden."
        const val KEY_FIRST_RUN_COMPLETE = "app.firstRunComplete"
    }
}
