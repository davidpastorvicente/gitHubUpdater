package com.davidpv.githubupdater.install

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.davidpv.githubupdater.GitHubUpdaterApplication
import com.davidpv.githubupdater.data.model.InstallProgress
import com.davidpv.githubupdater.data.model.InstallStage
import com.davidpv.githubupdater.data.model.ReleaseAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeJobs = mutableMapOf<String, Job>()

    private lateinit var installer: ReleaseInstaller
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var wifiLock: WifiManager.WifiLock

    override fun onCreate() {
        super.onCreate()
        installer = (application as GitHubUpdaterApplication).container.releaseInstaller
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GitHubUpdater::Download")
        wifiLock = (getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "GitHubUpdater::Download")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
                val assetJson = intent.getStringExtra(EXTRA_ASSET_JSON) ?: return START_NOT_STICKY
                val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "App"
                val asset = Json.decodeFromString<SerializableAsset>(assetJson).toReleaseAsset()
                startDownload(packageName, displayName, asset)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
                cancelDownload(packageName)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDownload(packageName: String, displayName: String, asset: ReleaseAsset) {
        if (activeJobs.containsKey(packageName)) return

        acquireLocks()
        promoteToForeground(packageName, displayName)

        val job = serviceScope.launch {
            runCatching {
                installer.install(packageName, asset) { progress ->
                    _progressEvents.tryEmit(DownloadProgressEvent(packageName, progress))
                    updateNotification(packageName, displayName, progress)
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    _progressEvents.tryEmit(
                        DownloadProgressEvent(packageName, null, error.message ?: "Download failed.")
                    )
                }
            }
            activeJobs.remove(packageName)
            stopForegroundIfIdle()
        }
        activeJobs[packageName] = job
    }

    private fun cancelDownload(packageName: String) {
        activeJobs.remove(packageName)?.cancel()
        _progressEvents.tryEmit(DownloadProgressEvent(packageName, null, cancelled = true))
        stopForegroundIfIdle()
    }

    private fun promoteToForeground(packageName: String, displayName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(displayName)
            .setContentText("Starting download…")
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()

        ServiceCompat.startForeground(
            this,
            notificationIdFor(packageName),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun updateNotification(packageName: String, displayName: String, progress: InstallProgress) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(displayName)
            .setContentText(notificationText(progress))
            .setSubText(notificationSubtext(progress))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        val totalBytes = progress.totalBytes
        val downloadedBytes = progress.downloadedBytes
        val pct = if (totalBytes != null && totalBytes > 0L) {
            ((downloadedBytes.coerceAtLeast(0L) * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else null

        if (pct != null) builder.setProgress(100, pct, false)
        else builder.setProgress(0, 0, true)

        nm.notify(notificationIdFor(packageName), builder.build())
    }

    private fun stopForegroundIfIdle() {
        if (activeJobs.isEmpty()) {
            releaseLocks()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireLocks() {
        if (!wakeLock.isHeld) wakeLock.acquire(30 * 60 * 1000L) // 30 min timeout
        if (!wifiLock.isHeld) wifiLock.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
    }

    private fun notificationText(progress: InstallProgress): String {
        val downloaded = Formatter.formatFileSize(this, progress.downloadedBytes)
        val total = progress.totalBytes?.takeIf { it > 0L }?.let { Formatter.formatFileSize(this, it) }
        return if (total != null && progress.downloadedBytes > 0L) "$downloaded / $total"
        else notificationSubtext(progress)
    }

    private fun notificationSubtext(progress: InstallProgress): String = when (progress.stage) {
        InstallStage.CheckingCache -> "Checking cached APK"
        InstallStage.UsingCache -> "Using cached APK"
        InstallStage.Downloading -> "Downloading"
        InstallStage.Verifying -> "Verifying APK"
        InstallStage.PreparingInstall -> "Preparing installer"
        InstallStage.AwaitingConfirmation -> "Waiting for install confirmation"
    }

    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START_DOWNLOAD = "com.davidpv.githubupdater.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.davidpv.githubupdater.CANCEL_DOWNLOAD"
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_ASSET_JSON = "asset_json"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        const val CHANNEL_ID = "download_progress"

        private val _progressEvents = MutableSharedFlow<DownloadProgressEvent>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val progressEvents = _progressEvents.asSharedFlow()

        fun startDownload(context: Context, packageName: String, displayName: String, asset: ReleaseAsset) {
            val assetJson = Json.encodeToString(
                SerializableAsset.serializer(),
                SerializableAsset.from(asset),
            )
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_ASSET_JSON, assetJson)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context, packageName: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            context.startService(intent)
        }

        private fun notificationIdFor(packageName: String): Int = packageName.hashCode()
    }
}

data class DownloadProgressEvent(
    val packageName: String,
    val progress: InstallProgress? = null,
    val errorMessage: String? = null,
    val cancelled: Boolean = false,
)

@Serializable
private data class SerializableAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val downloadCount: Int,
) {
    fun toReleaseAsset() = ReleaseAsset(id, name, downloadUrl, sizeBytes, sha256, downloadCount)

    companion object {
        fun from(asset: ReleaseAsset) = SerializableAsset(
            id = asset.id,
            name = asset.name,
            downloadUrl = asset.downloadUrl,
            sizeBytes = asset.sizeBytes,
            sha256 = asset.sha256,
            downloadCount = asset.downloadCount,
        )
    }
}
