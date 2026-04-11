package com.davidpv.githubupdater.data

import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.GitHubAssetResponse
import com.davidpv.githubupdater.data.model.VersionRegexTarget

fun matchesReleaseRules(releaseTagName: String, app: AppCatalogEntry): Boolean {
    val releaseRegex = app.releaseRegex?.takeIf { it.isNotBlank() } ?: return true
    return Regex(releaseRegex).containsMatchIn(releaseTagName)
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
    val versionRegex = app.versionRegex?.takeIf { it.isNotBlank() }

    val versionSource = when (app.versionRegexTarget) {
        VersionRegexTarget.Release -> releaseTagName
        VersionRegexTarget.APK -> assetName
    }

    if (versionRegex != null) {
        val target = if ("\\.apk" !in versionRegex) versionSource.removeSuffix(".apk") else versionSource
        val extracted = Regex(versionRegex).find(target)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
        if (extracted != null) return extracted
    }

    // Auto-extract version from tag name (the release ID), then fall back to display name.
    return extractVersionFromString(releaseTagName)
        ?: extractVersionFromString(releaseName)
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
