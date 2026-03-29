package com.davidpv.updatermanager.data.local

import android.content.Context
import com.davidpv.updatermanager.data.model.AppCatalogEntry
import kotlinx.serialization.json.Json

class AppCatalogDataSource(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadSupportedApps(): List<AppCatalogEntry> {
        val body = context.assets.open(APPS_CATALOG_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        return json.decodeFromString<List<AppCatalogEntry>>(body)
            .also(::validateUniquePackageNames)
    }

    private fun validateUniquePackageNames(entries: List<AppCatalogEntry>) {
        val duplicatePackageNames = entries
            .groupBy(AppCatalogEntry::packageName)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
        check(duplicatePackageNames.isEmpty()) {
            "apps.json has duplicate packageName entries: ${duplicatePackageNames.joinToString()}. Each app must use a unique packageName."
        }
    }

    private companion object {
        const val APPS_CATALOG_FILE_NAME = "apps.json"
    }
}
