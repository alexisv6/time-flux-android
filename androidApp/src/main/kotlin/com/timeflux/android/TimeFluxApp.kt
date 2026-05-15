package com.timeflux.android

import android.app.Application
import com.timeflux.android.di.appModule
import com.timeflux.di.initKoin
import org.koin.android.ext.koin.androidContext

class TimeFluxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(additionalModules = listOf(appModule)) {
            androidContext(this@TimeFluxApp)
        }
    }
}
