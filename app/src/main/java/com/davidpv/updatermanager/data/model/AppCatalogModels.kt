package com.davidpv.updatermanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppCatalogEntry(
    val id: String,
    val displayName: String,
    val packageName: String,
    val releaseOwner: String,
    val releaseRepo: String,
    val includeAssetGlobs: List<String>,
    val excludeAssetGlobs: List<String> = emptyList(),
)
