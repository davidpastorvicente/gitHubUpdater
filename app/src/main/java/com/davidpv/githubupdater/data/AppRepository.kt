package com.davidpv.githubupdater.data

import com.davidpv.githubupdater.data.local.AppCatalogRepository
import com.davidpv.githubupdater.data.local.AppSettingsRepository
import com.davidpv.githubupdater.data.local.InstalledAppInspector
import com.davidpv.githubupdater.data.local.ReleaseCacheRepository
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
    private val releaseCacheRepository: ReleaseCacheRepository,
    private val appSettingsRepository: AppSettingsRepository,
) {
    private val cachedReleasesByPackageName = mutableMapOf<String, List<ReleaseItem>>()

    suspend fun loadManagedApps(
        forceRemoteRefresh: Boolean = false,
        useCachedRemoteDataOnly: Boolean = false,
    ): List<ManagedApp> {
        val supportedApps = appCatalogRepository.loadSupportedApps()
        val errors = mutableListOf<String>()
        val latestReleasesByPackageName = if (useCachedRemoteDataOnly) {
            emptyMap()
        } else {
            runCatching {
                releasesService.fetchLatestReleasesBatch(
                    apps = supportedApps,
                    gitHubToken = appSettingsRepository.currentSettings.gitHubToken,
                    forceRefresh = forceRemoteRefresh,
                )
            }.getOrElse { error ->
                errors += error.message ?: "Failed to fetch latest releases."
                emptyMap()
            }
        }
        val apps = supportedApps.map { supportedApp ->
            val releases = if (useCachedRemoteDataOnly) {
                cachedReleaseItemsFor(supportedApp)
            } else {
                latestReleasesByPackageName[supportedApp.packageName]
                    ?.also { releaseCacheRepository.save(supportedApp.packageName, it, includesHistory = false) }
                    ?.let { toReleaseItems(releases = it, app = supportedApp) }
                    ?.also { cachedReleasesByPackageName[supportedApp.packageName] = it }
                    ?: run {
                        errors += "${supportedApp.displayName}: Unable to load latest release."
                    cachedReleaseItemsFor(supportedApp)
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

    suspend fun loadHistory(
        packageName: String,
        forceRemoteRefresh: Boolean = false,
    ): List<ReleaseItem> {
        val app = appCatalogRepository.getEntry(packageName)
            ?: error("App with package name '$packageName' not found.")
        val releases = if (forceRemoteRefresh) {
            releasesService.fetchReleases(
                owner = app.releaseOwner,
                repo = app.releaseRepo,
                perPage = HISTORY_RELEASES_PER_PAGE,
                forceRefresh = true,
                gitHubToken = appSettingsRepository.currentSettings.gitHubToken,
            ).also { releaseCacheRepository.save(packageName, it, includesHistory = true) }
        } else {
            val cached = releaseCacheRepository.load(packageName)
            cached.takeIf { it.isNotEmpty() && releaseCacheRepository.hasHistory(packageName) } ?: run {
                releasesService.fetchReleases(
                    owner = app.releaseOwner,
                    repo = app.releaseRepo,
                    perPage = HISTORY_RELEASES_PER_PAGE,
                    forceRefresh = false,
                    gitHubToken = appSettingsRepository.currentSettings.gitHubToken,
                ).also { releaseCacheRepository.save(packageName, it, includesHistory = true) }
            }
        }
        return toReleaseItems(releases = releases, app = app)
            .also { cachedReleasesByPackageName[packageName] = it }
    }

    class PartialLoadException(
        val apps: List<ManagedApp>,
        val errors: List<String>,
    ) : Exception(deduplicateErrors(errors))

    private fun cachedReleaseItemsFor(app: AppCatalogEntry): List<ReleaseItem> {
        return cachedReleasesByPackageName[app.packageName]
            ?: toReleaseItems(releases = releaseCacheRepository.load(app.packageName), app = app)
                .also { cachedReleasesByPackageName[app.packageName] = it }
    }

    private fun toReleaseItems(
        releases: List<GitHubReleaseResponse>,
        app: AppCatalogEntry,
    ): List<ReleaseItem> {
        return releases.asSequence()
            .filterNot { it.draft || it.prerelease }
            .mapNotNull { toReleaseItem(release = it, app = app) }
            .sortedByDescending(ReleaseItem::publishedAt)
            .toList()
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
            versionName = resolvedVersionName(
                releaseTagName = release.tagName,
                assetName = asset.name,
                app = app,
            ),
            publishedAt = Instant.parse(release.publishedAt),
            asset = asset,
            changelog = release.body,
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
        if (installedVersion == latestVersion) return true
        if (compareVersions(installedVersion, latestVersion) == 0) return true
        // Handle builds where installed appends a suffix (e.g. git hash) to the release version:
        // "4.7.2-25.20.0_b05ff8e" should be considered current against "4.7.2-25.20.0"
        if (installedVersion.startsWith(latestVersion) &&
            installedVersion.length > latestVersion.length &&
            !installedVersion[latestVersion.length].isDigit()
        ) return true
        return false
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
        const val HISTORY_RELEASES_PER_PAGE = 10
        val versionRegex = Regex("\\d+")

        fun deduplicateErrors(errors: List<String>): String {
            val grouped = errors.groupBy { it.substringAfter(": ", it) }
            return grouped.map { (message, entries) ->
                if (entries.size > 1) message else entries.first()
            }.joinToString("\n")
        }
    }
}
