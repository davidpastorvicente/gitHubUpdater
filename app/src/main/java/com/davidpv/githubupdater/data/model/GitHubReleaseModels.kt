package com.davidpv.githubupdater.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseResponse(
    val id: Long,
    @SerialName("tag_name")
    val tagName: String,
    val name: String,
    val body: String = "",
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("published_at")
    val publishedAt: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubAssetResponse> = emptyList(),
)

@Serializable
data class GitHubAssetResponse(
    val id: Long,
    val name: String,
    val size: Long,
    val digest: String? = null,
    @SerialName("download_count")
    val downloadCount: Int = 0,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
)
