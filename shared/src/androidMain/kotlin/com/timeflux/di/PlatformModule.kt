package com.timeflux.di

import android.content.Context
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import com.timeflux.data.db.DriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    single { DriverFactory(androidContext()) }
    single<ObservableSettings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("time_flux_settings", Context.MODE_PRIVATE)
        )
    }
}
