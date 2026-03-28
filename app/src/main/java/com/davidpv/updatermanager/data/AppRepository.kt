package com.davidpv.updatermanager.data

import com.davidpv.updatermanager.data.local.InstalledAppInspector
import com.davidpv.updatermanager.data.model.AvailabilityState
import com.davidpv.updatermanager.data.model.GitHubAssetResponse
import com.davidpv.updatermanager.data.model.GitHubReleaseResponse
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.data.model.ReleaseAsset
import com.davidpv.updatermanager.data.model.ReleaseItem
import com.davidpv.updatermanager.data.remote.GitHubReleasesService
import java.time.Instant

class AppRepository(
    private val releasesService: GitHubReleasesService,
    private val installedAppInspector: InstalledAppInspector,
) {
    suspend fun loadManagedApps(): List<ManagedApp> {
        return supportedApps.map { supportedApp ->
            val releases = releasesService.fetchReleases(
                owner = supportedApp.releaseOwner,
                repo = supportedApp.releaseRepo,
            )
                .asSequence()
                .filterNot { it.draft || it.prerelease }
                .mapNotNull(::toReleaseItem)
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
                id = supportedApp.id,
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

    private fun toReleaseItem(release: GitHubReleaseResponse): ReleaseItem? {
        val asset = release.assets
            .firstOrNull(::isPreferredTwitterAsset)
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

    private fun isPreferredTwitterAsset(asset: GitHubAssetResponse): Boolean {
        val name = asset.name.lowercase()
        return name.endsWith(".apk") &&
            "twitter-piko" in name &&
            "material-you" !in name &&
            !name.startsWith("x-")
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

    private data class SupportedApp(
        val id: String,
        val displayName: String,
        val packageName: String,
        val releaseOwner: String,
        val releaseRepo: String,
    )

    private companion object {
        val versionRegex = Regex("\\d+")

        val supportedApps = listOf(
            SupportedApp(
                id = "twitter",
                displayName = "Twitter",
                packageName = "com.twitter.android",
                releaseOwner = "crimera",
                releaseRepo = "twitter-apk",
            ),
        )
    }
}
