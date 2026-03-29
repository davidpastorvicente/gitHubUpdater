package com.davidpv.updatermanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
                    installer = releaseInstaller,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            val latestApps by rememberUpdatedState(state.apps)

            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                                "$displayName installed",
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

                        InstallResultStatus.Failed -> Unit
                    }
                }
            }

            UpdaterManagerTheme {
                MainScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onPrimaryAction = viewModel::onPrimaryAction,
                    onCancelInstall = viewModel::cancelInstall,
                    onOpenAppDetails = viewModel::openAppDetails,
                    onCloseAppDetails = viewModel::closeAppDetails,
                )
            }
        }
    }
}
