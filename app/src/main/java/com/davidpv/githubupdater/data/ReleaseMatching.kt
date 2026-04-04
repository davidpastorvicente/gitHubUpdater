package com.davidpv.githubupdater.data

import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.GitHubAssetResponse

fun matchesAssetRules(asset: GitHubAssetResponse, app: AppCatalogEntry): Boolean {
    if (!asset.name.endsWith(".apk")) return false
    val apkRegex = app.apkRegex?.takeIf { it.isNotBlank() } ?: return true
    return Regex(apkRegex).containsMatchIn(asset.name)
}

fun resolvedVersionName(releaseTagName: String, assetName: String, app: AppCatalogEntry): String {
    val cleanTag = releaseTagName.removePrefix("v").removePrefix("V")
    val versionRegex = app.versionRegex?.takeIf { it.isNotBlank() } ?: return cleanTag
    val target = if ("\\.apk" !in versionRegex) assetName.removeSuffix(".apk") else assetName
    return Regex(versionRegex).find(target)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: cleanTag
}
