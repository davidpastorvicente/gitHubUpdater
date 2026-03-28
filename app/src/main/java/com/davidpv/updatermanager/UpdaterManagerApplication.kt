package com.davidpv.updatermanager

import android.app.Application
import com.davidpv.updatermanager.di.AppContainer

class UpdaterManagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
