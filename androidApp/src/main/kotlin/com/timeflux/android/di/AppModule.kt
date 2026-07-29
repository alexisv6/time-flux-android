package com.timeflux.android.di

import com.timeflux.android.ui.modules.ModulesViewModel
import com.timeflux.android.ui.timeline.TimelineViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Android-app-level Koin bindings (ViewModels and anything else that is androidApp-specific).
 * Registered via [com.timeflux.android.TimeFluxApp] so it sits on top of the shared module.
 */
val appModule = module {
    viewModel { TimelineViewModel(get(), get(), get(), get(), get()) }
    viewModel { ModulesViewModel(get()) }
}
