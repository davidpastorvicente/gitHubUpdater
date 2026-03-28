package com.davidpv.updatermanager.di

import android.content.Context
import com.davidpv.updatermanager.data.AppRepository
import com.davidpv.updatermanager.data.local.InstalledAppInspector
import com.davidpv.updatermanager.data.remote.GitHubReleasesService
import com.davidpv.updatermanager.install.ReleaseInstaller

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val releasesService = GitHubReleasesService()
    private val installedAppInspector = InstalledAppInspector(appContext)

    val appRepository = AppRepository(
        releasesService = releasesService,
        installedAppInspector = installedAppInspector,
    )

    val releaseInstaller = ReleaseInstaller(appContext)
}
