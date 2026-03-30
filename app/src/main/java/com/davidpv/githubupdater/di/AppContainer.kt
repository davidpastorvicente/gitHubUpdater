package com.davidpv.githubupdater.di

import android.content.Context
import com.davidpv.githubupdater.data.AppRepository
import com.davidpv.githubupdater.data.local.AppCatalogRepository
import com.davidpv.githubupdater.data.local.AppSettingsRepository
import com.davidpv.githubupdater.data.local.InstalledAppInspector
import com.davidpv.githubupdater.data.remote.GitHubReleasesService
import com.davidpv.githubupdater.install.ApkDownloadStore
import com.davidpv.githubupdater.install.ReleaseInstaller

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appCatalogRepository = AppCatalogRepository(appContext)
    val appSettingsRepository = AppSettingsRepository(appContext)
    val releasesService = GitHubReleasesService()
    private val installedAppInspector = InstalledAppInspector(appContext)
    private val apkDownloadStore = ApkDownloadStore(appContext, appSettingsRepository)

    val appRepository = AppRepository(
        appCatalogRepository = appCatalogRepository,
        releasesService = releasesService,
        installedAppInspector = installedAppInspector,
    )

    val releaseInstaller = ReleaseInstaller(appContext, apkDownloadStore)
}
