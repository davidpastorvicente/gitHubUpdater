package com.davidpv.updatermanager.data.remote

import com.davidpv.updatermanager.data.model.GitHubReleaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleasesService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchReleases(
        owner: String,
        repo: String,
        perPage: Int = 12,
    ): List<GitHubReleaseResponse> = withContext(Dispatchers.IO) {
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
            "GitHub API error $responseCode: ${body.ifBlank { "No details" }}"
        }

        json.decodeFromString<List<GitHubReleaseResponse>>(body)
    }

    private companion object {
        const val USER_AGENT = "UpdaterManager/0.1"
    }
}
