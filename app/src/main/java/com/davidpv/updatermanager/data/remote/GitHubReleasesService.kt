package com.davidpv.updatermanager.data.remote

import com.davidpv.updatermanager.data.model.GitHubReleaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class GitHubReleasesService {
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedResponses = mutableMapOf<CacheKey, CachedReleases>()

    suspend fun fetchReleases(
        owner: String,
        repo: String,
        perPage: Int = 12,
        forceRefresh: Boolean = false,
    ): List<GitHubReleaseResponse> = withContext(Dispatchers.IO) {
        val cacheKey = CacheKey(owner = owner, repo = repo, perPage = perPage)
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
        const val USER_AGENT = "UpdaterManager/0.1"
        const val CACHE_TTL_MILLIS = 15 * 60 * 1000L
        val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }

    private data class CacheKey(
        val owner: String,
        val repo: String,
        val perPage: Int,
    )

    private data class CachedReleases(
        val releases: List<GitHubReleaseResponse>,
        val fetchedAtMillis: Long,
    )
}
