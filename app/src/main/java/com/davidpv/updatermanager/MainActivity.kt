package com.davidpv.updatermanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidpv.updatermanager.install.InstallResultEvents
import com.davidpv.updatermanager.install.InstallResultStatus
import com.davidpv.updatermanager.ui.MainScreen
import com.davidpv.updatermanager.ui.MainViewModel
import com.davidpv.updatermanager.ui.theme.UpdaterManagerTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val application = application as UpdaterManagerApplication
            val releaseInstaller = application.container.releaseInstaller
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(
                    repository = application.container.appRepository,
                    settingsRepository = application.container.appSettingsRepository,
                    installer = releaseInstaller,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val latestApps by rememberUpdatedState(state.apps)
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

            LaunchedEffect(Unit) {
                InstallResultEvents.events.collectLatest { event ->
                    val displayName = latestApps
                        .firstOrNull { it.packageName == event.packageName }
                        ?.displayName
                        ?: "App"
                    when (event.status) {
                        InstallResultStatus.Success -> {
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
                            Toast.makeText(
                                this@MainActivity,
                                event.failureMessage ?: "$displayName installation failed",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }

            UpdaterManagerTheme(
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
                )
            }
        }
    }
}
