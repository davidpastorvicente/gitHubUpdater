package com.davidpv.githubupdater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidpv.githubupdater.data.model.InstallProgress
import com.davidpv.githubupdater.data.model.InstallStage
import com.davidpv.githubupdater.data.model.ManagedApp
import com.davidpv.githubupdater.install.InstallResultEvents
import com.davidpv.githubupdater.install.InstallResultStatus
import com.davidpv.githubupdater.ui.MainScreen
import com.davidpv.githubupdater.ui.MainViewModel
import com.davidpv.githubupdater.ui.theme.GitHubUpdaterTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val activeDownloadNotificationIds = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureDownloadNotificationChannel()

        setContent {
            val application = application as GitHubUpdaterApplication
            val container = application.container
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(
                    repository = container.appRepository,
                    catalogRepository = container.appCatalogRepository,
                    settingsRepository = container.appSettingsRepository,
                    releasesService = container.releasesService,
                    installer = container.releaseInstaller,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocalStatus()
            }
            val latestApps by rememberUpdatedState(state.apps)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { }
            val pickDownloadFolderLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    viewModel.setCustomDownloadLocation(uri)
                } catch (_: SecurityException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to keep access to the selected folder",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            val importConfigLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read file.")
                }.onSuccess { json ->
                    viewModel.importApps(json)
                    Toast.makeText(this@MainActivity, "Configuration imported", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(this@MainActivity, e.message ?: "Import failed", Toast.LENGTH_LONG).show()
                }
            }

            val exportConfigLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                runCatching {
                    val json = viewModel.exportAppsJson()
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: throw IllegalStateException("Could not write file.")
                }.onSuccess {
                    Toast.makeText(this@MainActivity, "Configuration exported", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(this@MainActivity, e.message ?: "Export failed", Toast.LENGTH_LONG).show()
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(Unit) {
                InstallResultEvents.events.collectLatest { event ->
                    val displayName = latestApps
                        .firstOrNull { it.packageName == event.packageName }
                        ?.displayName
                        ?: "App"
                    when (event.status) {
                        InstallResultStatus.Success -> {
                            viewModel.refreshLocalStatus()
                            Toast.makeText(
                                this@MainActivity,
                                if (event.cleanupFailed) {
                                    "$displayName installed, but the APK couldn't be deleted"
                                } else {
                                    "$displayName installed"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }

                        InstallResultStatus.Cancelled -> {
                            Toast.makeText(
                                this@MainActivity,
                                "$displayName installation cancelled",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }

                        InstallResultStatus.Failed -> {
                            // Error card is already shown in the UI
                        }
                    }
                }
            }

            LaunchedEffect(state.installProgressByPackageName, latestApps) {
                syncDownloadNotifications(
                    progressByPackageName = state.installProgressByPackageName,
                    apps = latestApps,
                )
            }

            GitHubUpdaterTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.useDynamicColor,
            ) {
                MainScreen(
                    state = state,
                    onRefresh = viewModel::refreshNow,
                    onPrimaryAction = viewModel::onPrimaryAction,
                    onCancelInstall = viewModel::cancelInstall,
                    onOpenSettings = viewModel::openSettings,
                    onCloseSettings = viewModel::closeSettings,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetDynamicColor = viewModel::setDynamicColor,
                    onSetDeleteApkAfterInstall = viewModel::setDeleteApkAfterInstall,
                    onPickDownloadFolder = { pickDownloadFolderLauncher.launch(null) },
                    onUseDefaultDownloadLocation = viewModel::useDefaultDownloadLocation,
                    onOpenAppDetails = viewModel::openAppDetails,
                    onCloseAppDetails = viewModel::closeAppDetails,
                    onAddApp = viewModel::addApp,
                    onUpdateApp = viewModel::updateApp,
                    onDeleteApp = viewModel::deleteApp,
                    onTestFetchReleases = viewModel::testFetchReleases,
                    onGetCatalogEntry = viewModel::catalogEntry,
                    onImportConfig = { importConfigLauncher.launch(arrayOf("application/json", "*/*")) },
                    onExportConfig = { exportConfigLauncher.launch("apps.json") },
                )
            }
        }
    }

    private fun ensureDownloadNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows updater download progress"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun syncDownloadNotifications(
        progressByPackageName: Map<String, InstallProgress>,
        apps: List<ManagedApp>,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(this)
        val currentNotificationIds = progressByPackageName.keys.map(::downloadNotificationIdFor).toSet()
        (activeDownloadNotificationIds - currentNotificationIds).forEach(notificationManager::cancel)
        activeDownloadNotificationIds.clear()
        activeDownloadNotificationIds += currentNotificationIds

        progressByPackageName.forEach { (packageName, progress) ->
            val displayName = apps.firstOrNull { it.packageName == packageName }?.displayName ?: "App"
            notificationManager.notify(
                downloadNotificationIdFor(packageName),
                NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(displayName)
                    .setContentText(downloadNotificationText(progress))
                    .setSubText(downloadNotificationSubtext(progress))
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setSilent(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .applyDownloadProgress(progress)
                    .build(),
            )
        }
    }

    private fun NotificationCompat.Builder.applyDownloadProgress(progress: InstallProgress): NotificationCompat.Builder {
        val totalBytes = progress.totalBytes
        val downloadedBytes = progress.downloadedBytes
        val progressValue = if (totalBytes != null && totalBytes > 0L) {
            ((downloadedBytes.coerceAtLeast(0L) * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            null
        }
        return if (progressValue != null) {
            setProgress(100, progressValue, false)
        } else {
            setProgress(0, 0, true)
        }
    }

    private fun downloadNotificationText(progress: InstallProgress): String {
        val downloaded = Formatter.formatFileSize(this, progress.downloadedBytes)
        val total = progress.totalBytes?.takeIf { it > 0L }?.let { Formatter.formatFileSize(this, it) }
        return if (total != null && progress.downloadedBytes > 0L) {
            "$downloaded / $total"
        } else {
            downloadNotificationSubtext(progress)
        }
    }

    private fun downloadNotificationSubtext(progress: InstallProgress): String = when (progress.stage) {
        InstallStage.CheckingCache -> "Checking cached APK"
        InstallStage.UsingCache -> "Using cached APK"
        InstallStage.Downloading -> "Downloading"
        InstallStage.Verifying -> "Verifying APK"
        InstallStage.PreparingInstall -> "Preparing installer"
        InstallStage.AwaitingConfirmation -> "Waiting for install confirmation"
    }

    private fun downloadNotificationIdFor(packageName: String): Int = packageName.hashCode()

    private companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_progress"
    }
}
