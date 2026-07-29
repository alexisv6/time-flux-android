package com.timeflux.module

import com.russhwolf.settings.MapSettings
import com.timeflux.domain.model.ModuleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsModuleRegistryTest {

    private fun registry(settings: MapSettings = MapSettings()) =
        settings to SettingsModuleRegistry(settings)

    // ---- Defaults ----------------------------------------------------------------------

    @Test
    fun defaults_match_the_catalogue() = runTest {
        val (_, registry) = registry()

        assertEquals(
            AVAILABLE_MODULES.filter { it.enabledByDefault }.map { it.type }.toSet(),
            registry.observeEnabled().first(),
        )
        assertEquals(setOf(ModuleType.MILESTONE, ModuleType.MOOD), registry.observeEnabled().first())
        assertTrue(registry.observeHidden().first().isEmpty())
    }

    @Test
    fun unavailable_modules_are_never_enabled() = runTest {
        val (_, registry) = registry()

        assertFalse(registry.isEnabled(ModuleType.JOURNAL))
        assertTrue(ModuleType.JOURNAL !in registry.observeEnabled().first())
    }

    // ---- Enable / disable --------------------------------------------------------------

    @Test
    fun disabling_a_module_round_trips() = runTest {
        val (_, registry) = registry()

        registry.setEnabled(ModuleType.MOOD, false)

        assertFalse(registry.isEnabled(ModuleType.MOOD))
        assertEquals(setOf(ModuleType.MILESTONE), registry.observeEnabled().first())
    }

    @Test
    fun state_persists_across_registry_instances() = runTest {
        val settings = MapSettings()
        val (_, first) = registry(settings)
        first.setEnabled(ModuleType.MOOD, false)

        // Simulates a process restart: same stored settings, brand-new registry.
        val (_, second) = registry(settings)

        assertFalse(second.isEnabled(ModuleType.MOOD))
        assertEquals(setOf(ModuleType.MILESTONE), second.observeEnabled().first())
    }

    @Test
    fun toggling_an_unavailable_module_is_a_no_op() = runTest {
        val (settings, registry) = registry()

        registry.setEnabled(ModuleType.JOURNAL, true)

        assertFalse(registry.isEnabled(ModuleType.JOURNAL))
        assertTrue(ModuleType.JOURNAL !in registry.observeEnabled().first())
        assertFalse(settings.keys.any { it.contains(ModuleType.JOURNAL.id) })
    }

    // ---- Hiding ------------------------------------------------------------------------

    @Test
    fun a_disabled_module_can_be_hidden() = runTest {
        val (_, registry) = registry()

        registry.setEnabled(ModuleType.MOOD, false)
        registry.setHidden(ModuleType.MOOD, true)

        assertEquals(setOf(ModuleType.MOOD), registry.observeHidden().first())
    }

    @Test
    fun hiding_an_enabled_module_is_a_no_op() = runTest {
        val (_, registry) = registry()

        registry.setHidden(ModuleType.MOOD, true)

        assertTrue(registry.observeHidden().first().isEmpty())
    }

    @Test
    fun enabling_a_module_clears_its_hidden_flag() = runTest {
        val (_, registry) = registry()
        registry.setEnabled(ModuleType.MOOD, false)
        registry.setHidden(ModuleType.MOOD, true)

        registry.setEnabled(ModuleType.MOOD, true)

        assertTrue(
            registry.observeHidden().first().isEmpty(),
            "enabled-but-hidden would let the user create entries that never appear",
        )
    }

    @Test
    fun unhiding_leaves_the_module_disabled() = runTest {
        val (_, registry) = registry()
        registry.setEnabled(ModuleType.MOOD, false)
        registry.setHidden(ModuleType.MOOD, true)

        registry.setHidden(ModuleType.MOOD, false)

        assertTrue(registry.observeHidden().first().isEmpty())
        assertFalse(registry.isEnabled(ModuleType.MOOD))
    }

    // ---- Reactivity --------------------------------------------------------------------

    @Test
    fun observeEnabled_re_emits_after_a_write() = runTest {
        val (_, registry) = registry()
        val emissions = mutableListOf<Set<ModuleType>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.observeEnabled().toList(emissions)
        }

        registry.setEnabled(ModuleType.MOOD, false)

        assertEquals(
            listOf(setOf(ModuleType.MILESTONE, ModuleType.MOOD), setOf(ModuleType.MILESTONE)),
            emissions,
        )
        job.cancel()
    }

    @Test
    fun observeHidden_re_emits_after_a_write() = runTest {
        val (_, registry) = registry()
        registry.setEnabled(ModuleType.MOOD, false)
        val emissions = mutableListOf<Set<ModuleType>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.observeHidden().toList(emissions)
        }

        registry.setHidden(ModuleType.MOOD, true)

        assertEquals(listOf(emptySet(), setOf(ModuleType.MOOD)), emissions)
        job.cancel()
    }

    // ---- First run ---------------------------------------------------------------------

    @Test
    fun first_run_defaults_to_incomplete_and_sticks_once_set() = runTest {
        val settings = MapSettings()
        val (_, first) = registry(settings)
        assertFalse(first.isFirstRunComplete())

        first.completeFirstRun()

        assertTrue(first.isFirstRunComplete())
        val (_, second) = registry(settings)
        assertTrue(second.isFirstRunComplete(), "first-run state must survive a process restart")
    }
}
