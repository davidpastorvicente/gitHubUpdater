package com.davidpv.githubupdater.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.davidpv.githubupdater.data.model.AppSettings
import com.davidpv.githubupdater.data.model.DEFAULT_DOWNLOAD_DIRECTORY_DISPLAY_PATH
import com.davidpv.githubupdater.data.model.ThemeMode
import com.davidpv.githubupdater.data.model.VersionCompareDepth
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

    fun setGitHubToken(token: String?) {
        val normalizedToken = token?.trim().orEmpty().takeIf { it.isNotEmpty() }
        updateSettings(currentSettings.copy(gitHubToken = normalizedToken)) {
            if (normalizedToken == null) {
                remove(KEY_GITHUB_TOKEN)
            } else {
                putString(KEY_GITHUB_TOKEN, normalizedToken)
            }
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

    fun setVersionCompareDepth(depth: VersionCompareDepth) {
        updateSettings(currentSettings.copy(versionCompareDepth = depth)) {
            putString(KEY_VERSION_COMPARE_DEPTH, depth.name)
        }
    }

    fun setMirrorBaseUrl(url: String?) {
        val normalized = url?.trim().orEmpty().trimEnd('/').takeIf { it.isNotEmpty() }
        updateSettings(currentSettings.copy(mirrorBaseUrl = normalized)) {
            if (normalized == null) remove(KEY_MIRROR_BASE_URL) else putString(KEY_MIRROR_BASE_URL, normalized)
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
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_CUSTOM_DOWNLOAD_TREE_URI = "custom_download_tree_uri"
        private const val KEY_VERSION_COMPARE_DEPTH = "version_compare_depth"
        private const val KEY_MIRROR_BASE_URL = "mirror_base_url"
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
            gitHubToken = preferences.getString(KEY_GITHUB_TOKEN, null),
            customDownloadTreeUri = preferences.getString(KEY_CUSTOM_DOWNLOAD_TREE_URI, null),
            versionCompareDepth = preferences.getString(KEY_VERSION_COMPARE_DEPTH, null)
                ?.let { runCatching { VersionCompareDepth.valueOf(it) }.getOrNull() }
                ?: VersionCompareDepth.All,
            mirrorBaseUrl = preferences.getString(KEY_MIRROR_BASE_URL, null),
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
