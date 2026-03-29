package com.davidpv.updatermanager.di

import android.content.Context
import com.davidpv.updatermanager.data.AppRepository
import com.davidpv.updatermanager.data.local.AppCatalogRepository
import com.davidpv.updatermanager.data.local.AppSettingsRepository
import com.davidpv.updatermanager.data.local.InstalledAppInspector
import com.davidpv.updatermanager.data.remote.GitHubReleasesService
import com.davidpv.updatermanager.install.ApkDownloadStore
import com.davidpv.updatermanager.install.ReleaseInstaller

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
