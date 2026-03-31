package com.davidpv.githubupdater.data.local

import android.content.Context
import androidx.core.content.edit
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class ReleaseCacheRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(packageName: String): List<GitHubReleaseResponse> {
        val raw = preferences.getString(KEY_RELEASES_BY_PACKAGE, null) ?: return emptyList()
        val cached = runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), GitHubReleaseResponse.serializer().listSerializer()),
                raw,
            )
        }.getOrDefault(emptyMap())
        return cached[packageName].orEmpty()
    }

    fun save(packageName: String, releases: List<GitHubReleaseResponse>) {
        val current = loadAll().toMutableMap()
        current[packageName] = releases
        preferences.edit {
            putString(
                KEY_RELEASES_BY_PACKAGE,
                json.encodeToString(
                    MapSerializer(String.serializer(), GitHubReleaseResponse.serializer().listSerializer()),
                    current,
                ),
            )
        }
    }

    private fun loadAll(): Map<String, List<GitHubReleaseResponse>> {
        val raw = preferences.getString(KEY_RELEASES_BY_PACKAGE, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), GitHubReleaseResponse.serializer().listSerializer()),
                raw,
            )
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val PREFERENCES_NAME = "release_cache"
        const val KEY_RELEASES_BY_PACKAGE = "releases_by_package"
    }
}

private fun <T> kotlinx.serialization.KSerializer<T>.listSerializer() =
    kotlinx.serialization.builtins.ListSerializer(this)
