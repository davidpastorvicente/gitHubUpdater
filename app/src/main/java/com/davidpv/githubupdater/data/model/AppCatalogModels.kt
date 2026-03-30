package com.davidpv.githubupdater.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppCatalogEntry(
    val displayName: String,
    val packageName: String,
    val releaseOwner: String,
    val releaseRepo: String,
    val apkRegex: String? = null,
    val versionRegex: String? = null,
)
