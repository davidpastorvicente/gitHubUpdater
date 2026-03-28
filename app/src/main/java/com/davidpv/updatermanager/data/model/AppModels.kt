package com.davidpv.updatermanager.data.model

import java.time.Instant

data class InstalledAppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)

data class ReleaseAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val downloadCount: Int,
)

data class ReleaseItem(
    val id: Long,
    val versionName: String,
    val publishedAt: Instant,
    val asset: ReleaseAsset,
)

sealed interface AvailabilityState {
    data object NoRemoteRelease : AvailabilityState
    data object NotInstalled : AvailabilityState
    data class Current(val installedVersion: String) : AvailabilityState
    data class UpdateAvailable(
        val installedVersion: String,
        val latestVersion: String,
    ) : AvailabilityState
    data class InstalledVersionUnknown(val installedVersion: String) : AvailabilityState
}

enum class InstallStage {
    CheckingCache,
    UsingCache,
    Downloading,
    Verifying,
    PreparingInstall,
    AwaitingConfirmation,
}

data class InstallProgress(
    val stage: InstallStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
) {
    val progressFraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { downloadedBytes.toFloat() / it.toFloat() }
}

data class ManagedApp(
    val id: String,
    val displayName: String,
    val packageName: String,
    val installedVersionName: String?,
    val latestVersionName: String?,
    val availabilityState: AvailabilityState,
    val latestAsset: ReleaseAsset?,
    val history: List<ReleaseItem>,
)
