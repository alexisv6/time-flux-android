package com.timeflux.di

import com.timeflux.data.db.DriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    single { DriverFactory(androidContext()) }
}
