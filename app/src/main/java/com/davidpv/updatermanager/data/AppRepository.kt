package com.davidpv.updatermanager.data

import com.davidpv.updatermanager.data.local.AppCatalogDataSource
import com.davidpv.updatermanager.data.local.InstalledAppInspector
import com.davidpv.updatermanager.data.model.AppCatalogEntry
import com.davidpv.updatermanager.data.model.AvailabilityState
import com.davidpv.updatermanager.data.model.GitHubAssetResponse
import com.davidpv.updatermanager.data.model.GitHubReleaseResponse
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.data.model.ReleaseAsset
import com.davidpv.updatermanager.data.model.ReleaseItem
import com.davidpv.updatermanager.data.remote.GitHubReleasesService
import java.time.Instant

class AppRepository(
    private val appCatalogDataSource: AppCatalogDataSource,
    private val releasesService: GitHubReleasesService,
    private val installedAppInspector: InstalledAppInspector,
) {
    suspend fun loadManagedApps(forceRemoteRefresh: Boolean = false): List<ManagedApp> {
        return appCatalogDataSource.loadSupportedApps().map { supportedApp ->
            val releases = releasesService.fetchReleases(
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
    }

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
            versionName = release.tagName,
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
        val name = asset.name.lowercase()
        return globToRegex(app.assetGlob).matches(name)
    }

    private fun isVersionCurrent(installedVersion: String, latestVersion: String): Boolean {
        return installedVersion == latestVersion || compareVersions(installedVersion, latestVersion) == 0
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

        fun globToRegex(glob: String): Regex {
            val escaped = buildString {
                append("^")
                glob.lowercase().forEach { char ->
                    when (char) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
                            append('\\')
                            append(char)
                        }
                        else -> append(char)
                    }
                }
                append("$")
            }
            return Regex(escaped)
        }
    }
}
