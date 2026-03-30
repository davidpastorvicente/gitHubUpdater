package com.davidpv.githubupdater.install

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.davidpv.githubupdater.data.local.AppSettingsRepository
import com.davidpv.githubupdater.data.model.InstallProgress
import com.davidpv.githubupdater.data.model.InstallStage
import com.davidpv.githubupdater.data.model.ReleaseAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest

data class DownloadedApk(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
)

class ApkDownloadStore(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
) {
    private val contentResolver = context.contentResolver

    val shouldDeleteApkAfterInstall: Boolean
        get() = settingsRepository.currentSettings.deleteApkAfterInstall

    suspend fun prepareApk(
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ): DownloadedApk = withContext(Dispatchers.IO) {
        val customTreeUri = settingsRepository.currentSettings.customDownloadTreeUri?.let(Uri::parse)
        if (customTreeUri != null) {
            prepareInCustomFolder(customTreeUri, asset, onProgress)
        } else {
            prepareInPublicDownloads(asset, onProgress)
        }
    }

    private suspend fun prepareInPublicDownloads(
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ): DownloadedApk {
        onProgress(InstallProgress(stage = InstallStage.CheckingCache))
        val existing = findPublicDownload(asset.name)
        if (existing != null && isStoredApkValid(existing.uri, asset, onProgress)) {
            onProgress(
                InstallProgress(
                    stage = InstallStage.UsingCache,
                    downloadedBytes = existing.sizeBytes,
                    totalBytes = existing.sizeBytes,
                ),
            )
            return existing
        }
        existing?.let { deleteUri(it.uri) }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, asset.name)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, AppSettingsRepository.DEFAULT_DOWNLOAD_RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create the public download file.")

        return try {
            val downloadedApk = downloadToUri(asset, uri, onProgress)
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            downloadedApk
        } catch (error: Throwable) {
            deleteUri(uri)
            throw error
        }
    }

    private suspend fun prepareInCustomFolder(
        treeUri: Uri,
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ): DownloadedApk {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("The selected download folder is unavailable.")

        onProgress(InstallProgress(stage = InstallStage.CheckingCache))
        val existing = root.findFile(asset.name)
        if (existing != null && isStoredApkValid(existing.uri, asset, onProgress)) {
            val sizeBytes = querySize(existing.uri) ?: asset.sizeBytes
            onProgress(
                InstallProgress(
                    stage = InstallStage.UsingCache,
                    downloadedBytes = sizeBytes,
                    totalBytes = sizeBytes,
                ),
            )
            return DownloadedApk(
                uri = existing.uri,
                displayName = existing.name ?: asset.name,
                sizeBytes = sizeBytes,
            )
        }
        existing?.delete()

        val outputFile = root.createFile(APK_MIME_TYPE, asset.name)
            ?: error("Unable to create the download file in the selected folder.")

        return try {
            downloadToUri(asset, outputFile.uri, onProgress)
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        }
    }

    private suspend fun downloadToUri(
        asset: ReleaseAsset,
        uri: Uri,
        onProgress: (InstallProgress) -> Unit,
    ): DownloadedApk = withContext(Dispatchers.IO) {
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

        try {
            connection.inputStream.buffered().use { input ->
                openOutputStream(uri).use { rawOutput ->
                    DigestOutputStream(rawOutput, digest).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
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
            }
        } catch (error: CancellationException) {
            throw error
        } finally {
            connection.disconnect()
        }

        val storedSize = querySize(uri) ?: downloadedBytes
        verifyStoredApk(
            uri = uri,
            expectedSha256 = asset.sha256,
            expectedSizeBytes = asset.sizeBytes,
            precomputedDigest = digest.digest().toHexString(),
            sizeBytes = storedSize,
            onProgress = onProgress,
        )

        DownloadedApk(
            uri = uri,
            displayName = asset.name,
            sizeBytes = storedSize,
        )
    }

    private fun isStoredApkValid(
        uri: Uri,
        asset: ReleaseAsset,
        onProgress: (InstallProgress) -> Unit,
    ): Boolean {
        return runCatching {
            verifyStoredApk(
                uri = uri,
                expectedSha256 = asset.sha256,
                expectedSizeBytes = asset.sizeBytes,
                precomputedDigest = null,
                sizeBytes = querySize(uri),
                onProgress = onProgress,
            )
            true
        }.getOrElse {
            deleteUri(uri)
            false
        }
    }

    private fun verifyStoredApk(
        uri: Uri,
        expectedSha256: String?,
        expectedSizeBytes: Long,
        precomputedDigest: String?,
        sizeBytes: Long?,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(
            InstallProgress(
                stage = InstallStage.Verifying,
                downloadedBytes = sizeBytes ?: 0L,
                totalBytes = sizeBytes?.takeIf { it > 0L },
            ),
        )

        if (expectedSha256 != null) {
            val actualDigest = precomputedDigest ?: calculateSha256(uri)
            check(actualDigest.equals(expectedSha256, ignoreCase = true)) {
                "Downloaded APK checksum mismatch."
            }
        } else if (expectedSizeBytes > 0L) {
            check((sizeBytes ?: 0L) == expectedSizeBytes) { "Downloaded APK size mismatch." }
        } else {
            check((sizeBytes ?: 0L) > 0L) { "Downloaded APK is empty." }
        }
    }

    private fun calculateSha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        } ?: error("Unable to open the downloaded APK for verification.")
        return digest.digest().toHexString()
    }

    private fun findPublicDownload(displayName: String): DownloadedApk? {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(displayName, AppSettingsRepository.DEFAULT_DOWNLOAD_RELATIVE_PATH)
        contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            val sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE))
            return DownloadedApk(
                uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                displayName = displayName,
                sizeBytes = sizeBytes,
            )
        }
        return null
    }

    private fun querySize(uri: Uri): Long? {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
            }
        }
        return null
    }

    private fun openOutputStream(uri: Uri): OutputStream {
        return contentResolver.openOutputStream(uri, "w")
            ?: error("Unable to open the download destination.")
    }

    private fun deleteUri(uri: Uri) {
        runCatching { contentResolver.delete(uri, null, null) }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val USER_AGENT = "GitHubUpdater/0.1"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
