package com.timeflux.di

import com.timeflux.data.db.DriverFactory
import com.timeflux.data.json.AppJson
import com.timeflux.data.repository.TimelineRepositoryImpl
import com.timeflux.db.TimeFluxDatabase
import com.timeflux.domain.repository.TimelineRepository
import com.timeflux.module.ModuleRegistry
import com.timeflux.module.SettingsModuleRegistry
import com.timeflux.module.milestone.CreateMilestoneUseCase
import com.timeflux.module.milestone.UpdateMilestoneUseCase
import com.timeflux.module.mood.CreateMoodEntryUseCase
import com.timeflux.module.mood.UpdateMoodEntryUseCase
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

fun initKoin(
    additionalModules: List<Module> = emptyList(),
    appDeclaration: KoinApplication.() -> Unit = {},
) = startKoin {
    appDeclaration()
    modules(sharedModule() + additionalModules)
}

expect fun platformModule(): Module

/**
 * Platform-agnostic bindings. The platform module (which provides [DriverFactory]) is
 * included first so its singleton is available when the shared module resolves it.
 */
private fun sharedModule(): List<Module> = listOf(
    platformModule(),
    module {
        // Database singleton — driver is created once and reused for the app's lifetime
        single { TimeFluxDatabase(get<DriverFactory>().createDriver()) }

        // Repository — bound to interface so the v2 sync impl can swap in transparently
        single<TimelineRepository> { TimelineRepositoryImpl(get()) }

        // Shared JSON codec — one configured instance for all payload encode/decode
        single { AppJson }

        // Module enable/hide state — key-value backed, never the database (principle 1)
        single<ModuleRegistry> { SettingsModuleRegistry(get()) }

        // Module use cases — factory so each caller gets its own (stateless, cheap)
        factory { CreateMilestoneUseCase(get(), get()) }
        factory { UpdateMilestoneUseCase(get(), get()) }
        factory { CreateMoodEntryUseCase(get(), get()) }
        factory { UpdateMoodEntryUseCase(get(), get()) }
    },
)
