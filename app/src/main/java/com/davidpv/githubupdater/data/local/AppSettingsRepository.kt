package com.davidpv.githubupdater.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.davidpv.githubupdater.data.model.AppSettings
import com.davidpv.githubupdater.data.model.DEFAULT_DOWNLOAD_DIRECTORY_DISPLAY_PATH
import com.davidpv.githubupdater.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val currentSettings: AppSettings
        get() = _settings.value

    fun setThemeMode(themeMode: ThemeMode) {
        updateSettings(currentSettings.copy(themeMode = themeMode)) {
            putString(KEY_THEME_MODE, themeMode.name)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        updateSettings(currentSettings.copy(useDynamicColor = enabled)) {
            putBoolean(KEY_DYNAMIC_COLOR, enabled)
        }
    }

    fun setDeleteApkAfterInstall(enabled: Boolean) {
        updateSettings(currentSettings.copy(deleteApkAfterInstall = enabled)) {
            putBoolean(KEY_DELETE_APK_AFTER_INSTALL, enabled)
        }
    }

    fun setRefreshOnStart(enabled: Boolean) {
        updateSettings(currentSettings.copy(refreshOnStart = enabled)) {
            putBoolean(KEY_REFRESH_ON_START, enabled)
        }
    }

    fun setCustomDownloadTreeUri(uri: Uri?) {
        updateSettings(currentSettings.copy(customDownloadTreeUri = uri?.toString())) {
            if (uri == null) {
                remove(KEY_CUSTOM_DOWNLOAD_TREE_URI)
            } else {
                putString(KEY_CUSTOM_DOWNLOAD_TREE_URI, uri.toString())
            }
        }
    }

    fun downloadLocationSummary(settings: AppSettings = currentSettings): String {
        val treeUri = settings.customDownloadTreeUri?.let(Uri::parse) ?: return DEFAULT_DOWNLOAD_DIRECTORY_DISPLAY_PATH
        val displayName = DocumentFile.fromTreeUri(appContext, treeUri)?.name
        return displayName ?: treeUri.toString()
    }

    companion object {
        const val DEFAULT_DOWNLOAD_RELATIVE_PATH = "Download/GitHubUpdater/"
        private const val PREFERENCES_NAME = "github_updater_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_DELETE_APK_AFTER_INSTALL = "delete_apk_after_install"
        private const val KEY_REFRESH_ON_START = "refresh_on_start"
        private const val KEY_CUSTOM_DOWNLOAD_TREE_URI = "custom_download_tree_uri"
    }

    private fun loadSettings(): AppSettings {
        val themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.System
        return AppSettings(
            themeMode = themeMode,
            useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, true),
            deleteApkAfterInstall = preferences.getBoolean(KEY_DELETE_APK_AFTER_INSTALL, true),
            refreshOnStart = preferences.getBoolean(KEY_REFRESH_ON_START, true),
            customDownloadTreeUri = preferences.getString(KEY_CUSTOM_DOWNLOAD_TREE_URI, null),
        )
    }

    private fun updateSettings(
        newSettings: AppSettings,
        editBlock: android.content.SharedPreferences.Editor.() -> Unit,
    ) {
        preferences.edit().apply {
            editBlock()
            apply()
        }
        _settings.value = newSettings
    }
}
