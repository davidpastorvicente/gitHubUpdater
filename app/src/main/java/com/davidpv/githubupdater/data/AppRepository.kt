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
import com.davidpv.githubupdater.data.model.VersionCompareDepth
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
                catalogId = supportedApp.id,
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
        val app = appCatalogRepository.getEntryByPackageName(packageName)
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
        if (!matchesReleaseRules(releaseTagName = release.tagName, app = app)) return null

        val asset = release.assets
            .firstOrNull { asset -> matchesAssetRules(asset = asset, app = app) }
            ?.toReleaseAsset()

        // If a releaseRegex is set with no apkRegex, an APK asset is not required — the release itself is the unit.
        val hasApkRequirement = app.releaseRegex == null || !app.apkRegex.isNullOrBlank()
        if (hasApkRequirement && asset == null) return null
        // If releaseRegex only (no apkRegex), still try to grab any APK for download
        val resolvedAsset = asset ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.toReleaseAsset()
            ?: return null

        return ReleaseItem(
            id = release.id,
            versionName = resolvedVersionName(
                releaseTagName = release.tagName,
                releaseName = release.name,
                assetName = resolvedAsset.name,
                app = app,
            ),
            publishedAt = Instant.parse(release.publishedAt),
            asset = resolvedAsset,
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

    private fun isVersionCurrent(installedVersion: String, latestVersion: String): Boolean {
        val depth = appSettingsRepository.currentSettings.versionCompareDepth
        if (installedVersion == latestVersion) return true
        if (compareVersions(installedVersion, latestVersion, depth) == 0) return true
        if (installedVersion.startsWith(latestVersion) &&
            installedVersion.length > latestVersion.length &&
            !installedVersion[latestVersion.length].isDigit()
        ) return true
        return false
    }

    private fun compareVersions(
        installedVersion: String,
        latestVersion: String,
        depth: VersionCompareDepth = appSettingsRepository.currentSettings.versionCompareDepth,
    ): Int? {
        val installedSegments = parseVersionSegments(installedVersion)
        val latestSegments = parseVersionSegments(latestVersion)
        if (installedSegments.isEmpty() || latestSegments.isEmpty()) return null

        val limit = when (depth) {
            VersionCompareDepth.Major -> 1
            VersionCompareDepth.Minor -> 2
            VersionCompareDepth.Patch -> 3
            VersionCompareDepth.All -> Int.MAX_VALUE
        }
        val left = installedSegments.take(limit)
        val right = latestSegments.take(limit)

        val maxSize = maxOf(left.size, right.size)
        repeat(maxSize) { index ->
            val l = left.getOrElse(index) { "" }
            val r = right.getOrElse(index) { "" }
            val cmp = compareSegments(l, r)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private companion object {
        const val HISTORY_RELEASES_PER_PAGE = 10
        private val segmentSplitRegex = Regex("[.\\-]")

        fun parseVersionSegments(version: String): List<String> =
            version.split(segmentSplitRegex).filter { it.isNotEmpty() }

        fun compareSegments(a: String, b: String): Int {
            val aNum = a.toLongOrNull()
            val bNum = b.toLongOrNull()
            return when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                else -> a.compareTo(b, ignoreCase = true)
            }
        }

        fun deduplicateErrors(errors: List<String>): String {
            val grouped = errors.groupBy { it.substringAfter(": ", it) }
            return grouped.map { (message, entries) ->
                if (entries.size > 1) message else entries.first()
            }.joinToString("\n")
        }
    }
}
