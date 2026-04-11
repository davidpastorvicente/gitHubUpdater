package com.davidpv.githubupdater.data.local

import android.content.Context
import androidx.core.content.edit
import com.davidpv.githubupdater.data.model.AppCatalogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class AppCatalogRepository(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }
    private val _apps = MutableStateFlow(loadFromDisk())

    fun loadSupportedApps(): List<AppCatalogEntry> =
        _apps.value.sortedBy { it.displayName.lowercase() }

    fun getEntry(id: Int): AppCatalogEntry? =
        _apps.value.firstOrNull { it.id == id }

    fun getEntryByPackageName(packageName: String): AppCatalogEntry? =
        _apps.value.firstOrNull { it.packageName == packageName }

    fun addApp(entry: AppCatalogEntry) {
        val current = _apps.value
        require(current.none { it.packageName == entry.packageName }) {
            "An app with package name '${entry.packageName}' already exists."
        }
        persist(current + entry.copy(id = nextId()))
    }

    fun updateApp(id: Int, entry: AppCatalogEntry) {
        val current = _apps.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        require(index >= 0) { "App not found." }
        if (entry.packageName != current[index].packageName) {
            require(current.none { it.packageName == entry.packageName }) {
                "An app with package name '${entry.packageName}' already exists."
            }
        }
        current[index] = entry.copy(id = id)
        persist(current)
    }

    fun deleteApp(id: Int) {
        persist(_apps.value.filter { it.id != id })
    }

    fun clearApps() {
        persist(emptyList())
    }

    fun importApps(entries: List<AppCatalogEntry>) {
        validateUniquePackageNames(entries)
        var nextId = 1
        persist(entries.map { it.copy(id = nextId++) })
    }

    fun exportAppsJson(): String {
        val sorted = _apps.value.sortedBy { it.displayName.lowercase() }
        val elements = sorted.map { entry ->
            val obj = prettyJson.encodeToJsonElement(entry).jsonObject
            JsonObject(obj.filterKeys { it != "id" })
        }
        return prettyJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()), elements)
    }

    private fun nextId(): Int =
        (_apps.value.maxOfOrNull { it.id } ?: 0) + 1

    private fun persist(apps: List<AppCatalogEntry>) {
        val raw = json.encodeToString(serializer = AppCatalogEntry.serializer().listSerializer(), apps)
        preferences.edit { putString(KEY_APPS, raw) }
        _apps.value = apps
    }

    private fun loadFromDisk(): List<AppCatalogEntry> {
        val raw = preferences.getString(KEY_APPS, null) ?: return emptyList()
        val entries = runCatching { json.decodeFromString<List<AppCatalogEntry>>(raw) }
            .getOrDefault(emptyList())
        if (entries.any { it.id == 0 }) {
            var nextId = (entries.maxOfOrNull { it.id } ?: 0) + 1
            val migrated = entries.map { if (it.id == 0) it.copy(id = nextId++) else it }
            val migratedRaw = json.encodeToString(serializer = AppCatalogEntry.serializer().listSerializer(), migrated)
            preferences.edit { putString(KEY_APPS, migratedRaw) }
            return migrated
        }
        return entries
    }

    private fun validateUniquePackageNames(entries: List<AppCatalogEntry>) {
        val duplicates = entries
            .groupBy(AppCatalogEntry::packageName)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
        require(duplicates.isEmpty()) {
            "Duplicate packageName entries: ${duplicates.joinToString()}. Each app must use a unique packageName."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_catalog"
        const val KEY_APPS = "apps"
    }
}

private fun <T> kotlinx.serialization.KSerializer<T>.listSerializer() =
    kotlinx.serialization.builtins.ListSerializer(this)
