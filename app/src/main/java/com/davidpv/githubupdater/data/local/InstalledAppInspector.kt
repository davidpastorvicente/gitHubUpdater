package com.davidpv.githubupdater.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.davidpv.githubupdater.data.model.InstalledAppInfo

class InstalledAppInspector(
    context: Context,
) {
    private val packageManager = context.packageManager

    fun inspectInstalledApp(packageName: String): InstalledAppInfo? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            InstalledAppInfo(
                packageName = packageName,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = packageInfo.longVersionCode,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
