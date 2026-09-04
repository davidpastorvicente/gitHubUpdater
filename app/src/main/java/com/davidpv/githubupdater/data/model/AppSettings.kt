package com.davidpv.githubupdater.data.model

const val DEFAULT_DOWNLOAD_DIRECTORY_DISPLAY_PATH = "Downloads/GitHubUpdater"

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class VersionCompareDepth {
    All,
    Patch,
    Minor,
    Major,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val useDynamicColor: Boolean = true,
    val deleteApkAfterInstall: Boolean = true,
    val refreshOnStart: Boolean = true,
    val gitHubToken: String? = null,
    val customDownloadTreeUri: String? = null,
    val versionCompareDepth: VersionCompareDepth = VersionCompareDepth.All,
) {
    val usesDefaultDownloadDirectory: Boolean
        get() = customDownloadTreeUri == null
}
