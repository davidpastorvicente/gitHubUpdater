package com.davidpv.githubupdater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidpv.githubupdater.data.model.AppAction
import com.davidpv.githubupdater.install.DownloadService
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
                    appContext = applicationContext,
                    repository = container.appRepository,
                    catalogRepository = container.appCatalogRepository,
                    settingsRepository = container.appSettingsRepository,
                    releasesService = container.releasesService,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val latestApps by rememberUpdatedState(state.apps)
            val latestPendingActions by rememberUpdatedState(state.pendingActionsByPackageName)
            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            when (intent.action) {
                                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                                    if (latestPendingActions[packageName] == AppAction.Uninstall) {
                                        val displayName = latestApps
                                            .firstOrNull { it.packageName == packageName }
                                            ?.displayName
                                            ?: "App"
                                        Toast.makeText(
                                            this@MainActivity,
                                            "$displayName uninstalled",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    viewModel.clearUninstall(packageName)
                                }
                                Intent.ACTION_PACKAGE_ADDED,
                                Intent.ACTION_PACKAGE_REPLACED,
                                Intent.ACTION_PACKAGE_REMOVED,
                                -> viewModel.clearUninstall(packageName)
                            }
                        }
                        viewModel.refreshLocalStatus()
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                    addDataScheme("package")
                }
                registerReceiver(receiver, filter)
                onDispose { unregisterReceiver(receiver) }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocalStatus()
            }
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
            val uninstallLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                val packageUri = result.data?.data
                val packageName = packageUri?.schemeSpecificPart ?: return@rememberLauncherForActivityResult
                if (result.resultCode == RESULT_CANCELED) {
                    viewModel.clearUninstall(packageName)
                    val displayName = latestApps
                        .firstOrNull { it.packageName == packageName }
                        ?.displayName
                        ?: "App"
                    Toast.makeText(
                        this@MainActivity,
                        "$displayName uninstall cancelled",
                        Toast.LENGTH_SHORT,
                    ).show()
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
                                    successLabel(event.action, cleanupVariant = true, displayName = displayName)
                                } else {
                                    successLabel(event.action, cleanupVariant = false, displayName = displayName)
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }

                        InstallResultStatus.Cancelled -> {
                            Toast.makeText(
                                this@MainActivity,
                                cancelLabel(event.action, displayName),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }

                        InstallResultStatus.Failed -> {
                            // Error card is already shown in the UI
                        }
                    }
                }
            }

            LaunchedEffect(state.installProgressByPackageName) {
                if (state.installProgressByPackageName.isEmpty()) {
                    clearDownloadNotifications()
                }
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
                    onRequestUninstall = { packageName ->
                        viewModel.startUninstall(packageName)
                        uninstallLauncher.launch(
                            Intent(Intent.ACTION_DELETE, "package:$packageName".toUri()).apply {
                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                            },
                        )
                    },
                    onOpenSettings = viewModel::openSettings,
                    onCloseSettings = viewModel::closeSettings,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetDynamicColor = viewModel::setDynamicColor,
                    onSetDeleteApkAfterInstall = viewModel::setDeleteApkAfterInstall,
                    onSetRefreshOnStart = viewModel::setRefreshOnStart,
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
            DownloadService.CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows updater download progress"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun clearDownloadNotifications() {
        val notificationManager = NotificationManagerCompat.from(this)
        activeDownloadNotificationIds.forEach(notificationManager::cancel)
        activeDownloadNotificationIds.clear()
    }

    private fun successLabel(action: AppAction, cleanupVariant: Boolean, displayName: String): String = when (action) {
        AppAction.Install -> if (cleanupVariant) {
            "$displayName installed, but the APK couldn't be deleted"
        } else {
            "$displayName installed"
        }
        AppAction.Update -> if (cleanupVariant) {
            "$displayName updated, but the APK couldn't be deleted"
        } else {
            "$displayName updated"
        }
        AppAction.Uninstall -> "$displayName uninstalled"
    }

    private fun cancelLabel(action: AppAction, displayName: String): String = when (action) {
        AppAction.Install -> "$displayName installation cancelled"
        AppAction.Update -> "$displayName update cancelled"
        AppAction.Uninstall -> "$displayName uninstall cancelled"
    }
}
