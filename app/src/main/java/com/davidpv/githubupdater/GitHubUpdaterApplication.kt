package com.davidpv.githubupdater

import android.app.Application
import com.davidpv.githubupdater.di.AppContainer

class GitHubUpdaterApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
