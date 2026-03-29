package com.davidpv.updatermanager.data.local

import android.content.Context
import com.davidpv.updatermanager.data.model.AppCatalogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class AppCatalogRepository(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _apps = MutableStateFlow(loadFromDisk())

    fun loadSupportedApps(): List<AppCatalogEntry> = _apps.value

    fun addApp(entry: AppCatalogEntry) {
        val current = _apps.value
        require(current.none { it.packageName == entry.packageName }) {
            "An app with package name '${entry.packageName}' already exists."
        }
        persist(current + entry)
    }

    fun updateApp(originalPackageName: String, entry: AppCatalogEntry) {
        val current = _apps.value.toMutableList()
        val index = current.indexOfFirst { it.packageName == originalPackageName }
        require(index >= 0) { "App with package name '$originalPackageName' not found." }
        if (entry.packageName != originalPackageName) {
            require(current.none { it.packageName == entry.packageName }) {
                "An app with package name '${entry.packageName}' already exists."
            }
        }
        current[index] = entry
        persist(current)
    }

    fun deleteApp(packageName: String) {
        persist(_apps.value.filter { it.packageName != packageName })
    }

    fun importApps(entries: List<AppCatalogEntry>) {
        validateUniquePackageNames(entries)
        persist(entries)
    }

    fun exportAppsJson(): String =
        json.encodeToString(serializer = AppCatalogEntry.serializer().listSerializer(), _apps.value)

    private fun persist(apps: List<AppCatalogEntry>) {
        val raw = json.encodeToString(serializer = AppCatalogEntry.serializer().listSerializer(), apps)
        preferences.edit { putString(KEY_APPS, raw) }
        _apps.value = apps
    }

    private fun loadFromDisk(): List<AppCatalogEntry> {
        val raw = preferences.getString(KEY_APPS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AppCatalogEntry>>(raw) }
            .getOrDefault(emptyList())
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
