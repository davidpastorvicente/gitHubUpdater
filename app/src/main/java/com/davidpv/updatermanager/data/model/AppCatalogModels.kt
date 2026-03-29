package com.davidpv.updatermanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppCatalogEntry(
    val displayName: String,
    val packageName: String,
    val releaseOwner: String,
    val releaseRepo: String,
    val assetRegex: String,
    val versionRegex: String? = null,
)
