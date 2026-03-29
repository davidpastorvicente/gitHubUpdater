package com.davidpv.updatermanager.data.model

import android.os.Environment

val DEFAULT_DOWNLOAD_DIRECTORY_DISPLAY_PATH =
    "${Environment.getExternalStorageDirectory().path}/Downloads/UpdaterManager"

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val useDynamicColor: Boolean = true,
    val deleteApkAfterInstall: Boolean = true,
    val customDownloadTreeUri: String? = null,
) {
    val usesDefaultDownloadDirectory: Boolean
        get() = customDownloadTreeUri == null
}
