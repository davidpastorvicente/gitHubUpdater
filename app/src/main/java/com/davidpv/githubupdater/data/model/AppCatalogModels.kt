package com.davidpv.githubupdater.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class VersionRegexTarget {
    @SerialName("apk") APK,
    @SerialName("release") Release
}

@Serializable
data class AppCatalogEntry(
    val id: Int = 0,
    val displayName: String,
    val packageName: String,
    val releaseOwner: String,
    val releaseRepo: String,
    val apkRegex: String? = null,
    val releaseRegex: String? = null,
    val versionRegex: String? = null,
    val versionRegexTarget: VersionRegexTarget = VersionRegexTarget.APK,
)
