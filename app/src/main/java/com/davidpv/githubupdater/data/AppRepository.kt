package com.davidpv.githubupdater.data

import com.davidpv.githubupdater.data.local.AppCatalogRepository
import com.davidpv.githubupdater.data.local.InstalledAppInspector
import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.AvailabilityState
import com.davidpv.githubupdater.data.model.GitHubAssetResponse
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import com.davidpv.githubupdater.data.model.ManagedApp
import com.davidpv.githubupdater.data.model.ReleaseAsset
import com.davidpv.githubupdater.data.model.ReleaseItem
import com.davidpv.githubupdater.data.remote.GitHubReleasesService
import java.time.Instant

class AppRepository(
    private val appCatalogRepository: AppCatalogRepository,
    private val releasesService: GitHubReleasesService,
    private val installedAppInspector: InstalledAppInspector,
) {
    private val cachedReleasesByPackageName = mutableMapOf<String, List<ReleaseItem>>()

    suspend fun loadManagedApps(
        forceRemoteRefresh: Boolean = false,
        useCachedRemoteDataOnly: Boolean = false,
    ): List<ManagedApp> {
        val errors = mutableListOf<String>()
        val apps = appCatalogRepository.loadSupportedApps().map { supportedApp ->
            val releases = if (useCachedRemoteDataOnly) {
                cachedReleasesByPackageName[supportedApp.packageName].orEmpty()
            } else {
                try {
                    releasesService.fetchReleases(
                        owner = supportedApp.releaseOwner,
                        repo = supportedApp.releaseRepo,
                        perPage = RELEASES_PER_PAGE,
                        forceRefresh = forceRemoteRefresh,
                    )
                        .asSequence()
                        .filterNot { it.draft || it.prerelease }
                        .mapNotNull { toReleaseItem(release = it, app = supportedApp) }
                        .sortedByDescending(ReleaseItem::publishedAt)
                        .toList()
                        .also { cachedReleasesByPackageName[supportedApp.packageName] = it }
                } catch (e: Exception) {
                    errors += "${supportedApp.displayName}: ${e.message}"
                    cachedReleasesByPackageName[supportedApp.packageName].orEmpty()
                }
            }

            val latestRelease = releases.firstOrNull()
            val installedApp = installedAppInspector.inspectInstalledApp(supportedApp.packageName)
            val availabilityState = when {
                latestRelease == null -> AvailabilityState.NoRemoteRelease
                installedApp == null -> AvailabilityState.NotInstalled
                isVersionCurrent(installedApp.versionName, latestRelease.versionName) -> {
                    AvailabilityState.Current(installedApp.versionName)
                }
                compareVersions(installedApp.versionName, latestRelease.versionName)?.let { it < 0 } == true -> {
                    AvailabilityState.UpdateAvailable(installedApp.versionName, latestRelease.versionName)
                }
                else -> AvailabilityState.InstalledVersionUnknown(installedApp.versionName)
            }

            ManagedApp(
                displayName = supportedApp.displayName,
                packageName = supportedApp.packageName,
                installedVersionName = installedApp?.versionName,
                latestVersionName = latestRelease?.versionName,
                availabilityState = availabilityState,
                latestAsset = latestRelease?.asset,
                history = releases,
            )
        }
        if (errors.isNotEmpty()) {
            throw PartialLoadException(apps = apps, errors = errors)
        }
        return apps
    }

    class PartialLoadException(
        val apps: List<ManagedApp>,
        val errors: List<String>,
    ) : Exception(errors.joinToString("\n"))

    private fun toReleaseItem(
        release: GitHubReleaseResponse,
        app: AppCatalogEntry,
    ): ReleaseItem? {
        val asset = release.assets
            .firstOrNull { asset -> matchesAssetRules(asset = asset, app = app) }
            ?.toReleaseAsset()
            ?: return null

        return ReleaseItem(
            id = release.id,
            versionName = resolvedVersionName(
                releaseTagName = release.tagName,
                assetName = asset.name,
                app = app,
            ),
            publishedAt = Instant.parse(release.publishedAt),
            asset = asset,
        )
    }

    private fun GitHubAssetResponse.toReleaseAsset(): ReleaseAsset {
        return ReleaseAsset(
            id = id,
            name = name,
            downloadUrl = browserDownloadUrl,
            sizeBytes = size,
            sha256 = digest?.removePrefix("sha256:"),
            downloadCount = downloadCount,
        )
    }

    private fun matchesAssetRules(
        asset: GitHubAssetResponse,
        app: AppCatalogEntry,
    ): Boolean {
        val apkRegex = app.apkRegex
            ?.takeIf { it.isNotBlank() }
            ?.removeSuffix("$")
            ?.plus("\\.apk$")
            ?: return asset.name.endsWith(".apk")
        return Regex(apkRegex).containsMatchIn(asset.name)
    }

    private fun isVersionCurrent(installedVersion: String, latestVersion: String): Boolean {
        return installedVersion == latestVersion || compareVersions(installedVersion, latestVersion) == 0
    }

    private fun resolvedVersionName(
        releaseTagName: String,
        assetName: String,
        app: AppCatalogEntry,
    ): String {
        val cleanTag = releaseTagName.removePrefix("v").removePrefix("V")
        val versionRegex = app.versionRegex
            ?.removeSuffix("$")?.plus("\\.apk$")
            ?: return cleanTag
        val regex = Regex(versionRegex)
        return regex.find(assetName)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: cleanTag
    }

    private fun compareVersions(installedVersion: String, latestVersion: String): Int? {
        val installedParts = versionRegex.findAll(installedVersion).map { it.value.toInt() }.toList()
        val latestParts = versionRegex.findAll(latestVersion).map { it.value.toInt() }.toList()
        if (installedParts.isEmpty() || latestParts.isEmpty()) return null

        val maxSize = maxOf(installedParts.size, latestParts.size)
        repeat(maxSize) { index ->
            val left = installedParts.getOrElse(index) { 0 }
            val right = latestParts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private companion object {
        const val RELEASES_PER_PAGE = 10
        val versionRegex = Regex("\\d+")
    }
}
