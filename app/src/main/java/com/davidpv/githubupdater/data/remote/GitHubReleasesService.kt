package com.davidpv.githubupdater.data.remote

import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.GitHubAssetResponse
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class GitHubReleasesService {
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedResponses = mutableMapOf<CacheKey, CachedReleases>()

    suspend fun fetchLatestReleasesBatch(
        apps: List<AppCatalogEntry>,
        gitHubToken: String?,
        forceRefresh: Boolean = false,
    ): Map<String, List<GitHubReleaseResponse>> = withContext(Dispatchers.IO) {
        if (apps.isEmpty()) return@withContext emptyMap()
        if (gitHubToken.isNullOrBlank()) {
            return@withContext apps.associate { app ->
                app.packageName to fetchReleases(
                    owner = app.releaseOwner,
                    repo = app.releaseRepo,
                    perPage = if (app.multiAppRepo) 10 else 1,
                    forceRefresh = forceRefresh,
                    gitHubToken = null,
                )
            }
        }

        val aliasesByPackage = apps.associate { app ->
            app.packageName to "repo_${app.packageName.replace(Regex("[^A-Za-z0-9_]"), "_")}"
        }
        val query = buildString {
            append("query BatchedLatestReleases {")
            apps.forEach { app ->
                val alias = aliasesByPackage.getValue(app.packageName)
                if (app.multiAppRepo) {
                    append(
                        """
                        $alias: repository(owner: "${app.releaseOwner}", name: "${app.releaseRepo}") {
                          releases(first: 10, orderBy: {field: CREATED_AT, direction: DESC}) {
                            nodes {
                              $RELEASE_FIELDS
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                } else {
                    append(
                        """
                        $alias: repository(owner: "${app.releaseOwner}", name: "${app.releaseRepo}") {
                          latestRelease {
                            $RELEASE_FIELDS
                          }
                        }
                        """.trimIndent(),
                    )
                }
            }
            append("}")
        }

        val response = postGraphQl(query = query, gitHubToken = gitHubToken)
        val data = response.jsonObject["data"]?.jsonObject.orEmpty()
        apps.associate { app ->
            val alias = aliasesByPackage.getValue(app.packageName)
            val repoData = data[alias]?.jsonObject
            val releases = if (app.multiAppRepo) {
                repoData?.get("releases")
                    ?.jsonObject
                    ?.get("nodes")
                    ?.let { json.decodeFromString<List<GraphQlReleaseNode>>(it.toString()) }
                    ?.map { it.toRestLikeResponse() }
                    .orEmpty()
            } else {
                repoData?.get("latestRelease")
                    ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                    ?.let { json.decodeFromString<GraphQlReleaseNode>(it.toString()) }
                    ?.toRestLikeResponse()
                    ?.let { listOf(it) }
                    .orEmpty()
            }
            app.packageName to releases
        }
    }

    suspend fun fetchReleases(
        owner: String,
        repo: String,
        perPage: Int = 12,
        forceRefresh: Boolean = false,
        gitHubToken: String? = null,
    ): List<GitHubReleaseResponse> = withContext(Dispatchers.IO) {
        val cacheKey = CacheKey(owner = owner, repo = repo, perPage = perPage, authKey = gitHubToken?.take(12))
        val now = System.currentTimeMillis()
        cachedResponses[cacheKey]
            ?.takeIf { !forceRefresh && now - it.fetchedAtMillis < CACHE_TTL_MILLIS }
            ?.let { return@withContext it.releases }

        val connection = URL("https://api.github.com/repos/$owner/$repo/releases?per_page=$perPage")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        gitHubToken?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(responseCode in 200..299) {
            formatErrorMessage(responseCode = responseCode, body = body, connection = connection)
        }

        json.decodeFromString<List<GitHubReleaseResponse>>(body).also { releases ->
            cachedResponses[cacheKey] = CachedReleases(
                releases = releases,
                fetchedAtMillis = now,
            )
        }
    }

    private fun postGraphQl(query: String, gitHubToken: String): kotlinx.serialization.json.JsonElement {
        val connection = URL(GRAPHQL_ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("Authorization", "Bearer $gitHubToken")
        val body = buildJsonObject { put("query", query) }.toString()
        connection.outputStream.bufferedWriter().use { it.write(body) }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(responseCode in 200..299) {
            formatErrorMessage(responseCode = responseCode, body = responseBody, connection = connection)
        }
        val element = json.parseToJsonElement(responseBody)
        val errors = element.jsonObject["errors"] as? JsonArray
        check(errors.isNullOrEmpty()) {
            errors?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.content
                ?: "GitHub GraphQL error."
        }
        return element
    }

    private fun formatErrorMessage(
        responseCode: Int,
        body: String,
        connection: HttpURLConnection,
    ): String {
        val githubMessage = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN &&
            githubMessage?.contains("rate limit exceeded", ignoreCase = true) == true
        ) {
            val resetAt = connection.getHeaderField("X-RateLimit-Reset")
                ?.toLongOrNull()
                ?.let { Instant.ofEpochSecond(it) }
                ?.atZone(ZoneId.systemDefault())
                ?.format(TIME_FORMATTER)
            return if (resetAt != null) {
                "GitHub rate limit reached. Try again after $resetAt."
            } else {
                "GitHub rate limit reached. Try again later."
            }
        }

        return githubMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { "GitHub API error $responseCode: $it" }
            ?: "GitHub API error $responseCode."
    }

    private companion object {
        const val USER_AGENT = "GitHubUpdater/0.1"
        const val GRAPHQL_ENDPOINT = "https://api.github.com/graphql"
        const val CACHE_TTL_MILLIS = 15 * 60 * 1000L
        val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        const val RELEASE_FIELDS = """
            databaseId
            tagName
            name
            description
            url
            publishedAt
            isDraft
            isPrerelease
            releaseAssets(first: 20) {
              nodes {
                id
                name
                size
                downloadCount
                downloadUrl
                digest
              }
            }
        """
    }

    private data class CacheKey(
        val owner: String,
        val repo: String,
        val perPage: Int,
        val authKey: String?,
    )

    private data class CachedReleases(
        val releases: List<GitHubReleaseResponse>,
        val fetchedAtMillis: Long,
    )

    @Serializable
    private data class GraphQlReleaseNode(
        val databaseId: Int? = null,
        val tagName: String,
        val name: String? = null,
        val description: String? = null,
        val url: String,
        val publishedAt: String,
        val isDraft: Boolean,
        val isPrerelease: Boolean,
        val releaseAssets: GraphQlReleaseAssets = GraphQlReleaseAssets(),
    ) {
        fun toRestLikeResponse(): GitHubReleaseResponse {
            return GitHubReleaseResponse(
                id = databaseId?.toLong() ?: tagName.hashCode().toLong(),
                tagName = tagName,
                name = name.orEmpty(),
                body = description.orEmpty(),
                htmlUrl = url,
                publishedAt = publishedAt,
                draft = isDraft,
                prerelease = isPrerelease,
                assets = releaseAssets.nodes.map { asset ->
                    GitHubAssetResponse(
                        id = asset.id.hashCode().toLong(),
                        name = asset.name,
                        size = asset.size,
                        digest = asset.digest,
                        downloadCount = asset.downloadCount,
                        browserDownloadUrl = asset.downloadUrl,
                    )
                },
            )
        }
    }

    @Serializable
    private data class GraphQlReleaseAssets(
        val nodes: List<GraphQlReleaseAssetNode> = emptyList(),
    )

    @Serializable
    private data class GraphQlReleaseAssetNode(
        val id: String,
        val name: String,
        val size: Long,
        val downloadCount: Int = 0,
        val downloadUrl: String,
        val digest: String? = null,
    )
}
