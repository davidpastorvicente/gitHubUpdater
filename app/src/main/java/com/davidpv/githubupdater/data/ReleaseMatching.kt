package com.davidpv.githubupdater.data

import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.GitHubAssetResponse

fun matchesReleaseRules(releaseName: String, app: AppCatalogEntry): Boolean {
    val releaseRegex = app.releaseRegex?.takeIf { it.isNotBlank() } ?: return true
    return Regex(releaseRegex).containsMatchIn(releaseName)
}

fun matchesAssetRules(asset: GitHubAssetResponse, app: AppCatalogEntry): Boolean {
    if (!asset.name.endsWith(".apk")) return false
    val apkRegex = app.apkRegex?.takeIf { it.isNotBlank() } ?: return true
    return Regex(apkRegex).containsMatchIn(asset.name)
}

fun resolvedVersionName(
    releaseTagName: String,
    releaseName: String,
    assetName: String,
    app: AppCatalogEntry,
): String {
    val hasReleaseRegex = !app.releaseRegex.isNullOrBlank()
    val hasApkRegex = !app.apkRegex.isNullOrBlank()
    val versionRegex = app.versionRegex?.takeIf { it.isNotBlank() }

    // Determine versionRegex target:
    // - Both releaseRegex + apkRegex set  → match APK filename
    // - Only releaseRegex set             → match release name
    // - Otherwise                         → match APK filename (original behaviour)
    val versionSource = if (hasReleaseRegex && !hasApkRegex) releaseName else assetName

    if (versionRegex != null) {
        val target = if ("\\.apk" !in versionRegex) versionSource.removeSuffix(".apk") else versionSource
        val extracted = Regex(versionRegex).find(target)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
        if (extracted != null) return extracted
    }

    // Auto-extract version from release name or tag when no versionRegex is set (or it failed).
    // Try release name first (richer label), then fall back to tag.
    return extractVersionFromString(releaseName)
        ?: extractVersionFromString(releaseTagName)
        ?: releaseTagName.removePrefix("v").removePrefix("V")
}

// Finds the first semver-like sequence in a string, stripping leading v/V prefix.
// e.g. "youtube-music-v2.1.4" → "2.1.4", "v1.1.0" → "1.1.0", "Release 13.6.0.r1086" → "13.6.0.r1086"
private val semverPattern = Regex("""[vV]?(\d+(?:[.\-]\w+)+)""")

private fun extractVersionFromString(input: String): String? {
    return semverPattern.find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}
