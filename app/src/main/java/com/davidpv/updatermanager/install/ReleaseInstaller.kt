package com.davidpv.updatermanager.install

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.davidpv.updatermanager.data.model.InstallProgress
import com.davidpv.updatermanager.data.model.InstallStage
import com.davidpv.updatermanager.data.model.ReleaseAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReleaseInstaller(
    private val context: Context,
    private val downloadStore: ApkDownloadStore,
) {
    suspend fun install(
        packageName: String,
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val downloadedApk = downloadStore.prepareApk(asset, onProgress)
            installWithPackageInstaller(packageName = packageName, downloadedApk = downloadedApk, onProgress = onProgress)
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private fun installWithPackageInstaller(
        packageName: String,
        downloadedApk: DownloadedApk,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(
            InstallProgress(
                stage = InstallStage.PreparingInstall,
                downloadedBytes = downloadedApk.sizeBytes,
                totalBytes = downloadedApk.sizeBytes,
            ),
        )

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        context.contentResolver.openInputStream(downloadedApk.uri)?.buffered()?.use { input ->
            session.openWrite(downloadedApk.displayName, 0, -1).use { output ->
                input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
                session.fsync(output)
            }
        } ?: error("Unable to open the downloaded APK for installation.")

        val intent = Intent(context, InstallResultReceiver::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_APK_URI, downloadedApk.uri.toString())
            putExtra(EXTRA_DELETE_APK_AFTER_INSTALL, downloadStore.shouldDeleteApkAfterInstall)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        session.commit(pendingIntent.intentSender)
        session.close()

        onProgress(
            InstallProgress(
                stage = InstallStage.AwaitingConfirmation,
                downloadedBytes = downloadedApk.sizeBytes,
                totalBytes = downloadedApk.sizeBytes,
            ),
        )
    }

    private companion object {
        const val EXTRA_PACKAGE_NAME = "install_package_name"
        const val EXTRA_APK_URI = "install_apk_uri"
        const val EXTRA_DELETE_APK_AFTER_INSTALL = "install_delete_apk_after_install"
    }

    private val installMutex = Mutex()
}
