package com.davidpv.updatermanager.install

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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest

class ReleaseInstaller(
    private val context: Context,
) {
    suspend fun install(
        packageName: String,
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val apkFile = prepareApkFile(asset, onProgress)
            check(apkFile.isFile) { "Prepared APK file is missing." }
            installWithPackageInstaller(packageName = packageName, apkFile = apkFile, onProgress = onProgress)
        }
    }

    private fun prepareApkFile(
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ): File {
        val storageDirectory = File(context.filesDir, APK_STORAGE_DIRECTORY_NAME)
        check(storageDirectory.exists() || storageDirectory.mkdirs()) {
            "Unable to create the APK storage directory."
        }
        val cacheFile = File(storageDirectory, "${asset.id}-${sanitizeFileName(asset.name)}")

        onProgress(InstallProgress(stage = InstallStage.CheckingCache))
        if (cacheFile.exists() && isCachedFileValid(cacheFile, asset, onProgress)) {
            onProgress(
                InstallProgress(
                    stage = InstallStage.UsingCache,
                    downloadedBytes = cacheFile.length(),
                    totalBytes = cacheFile.length(),
                ),
            )
            return cacheFile
        }

        val temporaryFile = File(storageDirectory, cacheFile.name + ".download")
        if (temporaryFile.exists()) {
            temporaryFile.delete()
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(asset.downloadUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)

        val responseCode = connection.responseCode
        check(responseCode in 200..299) { "Download failed with HTTP $responseCode." }

        val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: asset.sizeBytes.takeIf { it > 0L }
        var downloadedBytes = 0L

        connection.inputStream.buffered().use { input ->
            DigestOutputStream(FileOutputStream(temporaryFile), digest).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress(
                        InstallProgress(
                            stage = InstallStage.Downloading,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                        ),
                    )
                }
            }
        }

        verifyDownloadedFile(
            file = temporaryFile,
            expectedSha256 = asset.sha256,
            expectedSizeBytes = asset.sizeBytes,
            precomputedDigest = digest.digest().toHexString(),
            onProgress = onProgress,
        )

        check(temporaryFile.isFile) { "Downloaded APK was not written to disk." }
        temporaryFile.copyTo(cacheFile, overwrite = true)
        check(cacheFile.isFile) { "Failed to store the downloaded APK in cache." }
        temporaryFile.delete()
        return cacheFile
    }

    private fun isCachedFileValid(
        file: File,
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ): Boolean {
        return runCatching {
            verifyDownloadedFile(
                file = file,
                expectedSha256 = asset.sha256,
                expectedSizeBytes = asset.sizeBytes,
                precomputedDigest = null,
                onProgress = onProgress,
            )
            true
        }.getOrElse {
            file.delete()
            false
        }
    }

    private fun verifyDownloadedFile(
        file: File,
        expectedSha256: String?,
        expectedSizeBytes: Long,
        precomputedDigest: String?,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(
            InstallProgress(
                stage = InstallStage.Verifying,
                downloadedBytes = file.length(),
                totalBytes = file.length().takeIf { it > 0L },
            ),
        )

        if (expectedSha256 != null) {
            val actualDigest = precomputedDigest ?: calculateSha256(file)
            check(actualDigest.equals(expectedSha256, ignoreCase = true)) {
                "Downloaded APK checksum mismatch."
            }
        } else if (expectedSizeBytes > 0L) {
            check(file.length() == expectedSizeBytes) { "Downloaded APK size mismatch." }
        } else {
            check(file.length() > 0L) { "Downloaded APK is empty." }
        }
    }

    private fun installWithPackageInstaller(
        packageName: String,
        apkFile: File,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(
            InstallProgress(
                stage = InstallStage.PreparingInstall,
                downloadedBytes = apkFile.length(),
                totalBytes = apkFile.length(),
            ),
        )

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        FileInputStream(apkFile).buffered().use { input ->
            session.openWrite(apkFile.name, 0, apkFile.length()).use { output ->
                input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
                session.fsync(output)
            }
        }

        val intent = Intent(context, InstallResultReceiver::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
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
                downloadedBytes = apkFile.length(),
                totalBytes = apkFile.length(),
            ),
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().toHexString()
    }

    private fun sanitizeFileName(name: String): String = name.replace(ILLEGAL_FILENAME_CHARS, "_")

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val EXTRA_PACKAGE_NAME = "install_package_name"
        const val USER_AGENT = "UpdaterManager/0.1"
        const val APK_STORAGE_DIRECTORY_NAME = "release-downloads"
        val ILLEGAL_FILENAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }

    private val installMutex = Mutex()
}
