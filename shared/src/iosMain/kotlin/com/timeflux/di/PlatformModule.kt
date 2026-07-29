package com.timeflux.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import com.timeflux.data.db.DriverFactory
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule() = module {
    single { DriverFactory() }
    single<ObservableSettings> {
        NSUserDefaultsSettings(NSUserDefaults(suiteName = "com.timeflux.settings"))
    }
}
