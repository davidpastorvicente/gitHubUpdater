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
        return json.decodeFromString(body)
    }

    private companion object {
        const val APPS_CATALOG_FILE_NAME = "apps.json"
    }
}
