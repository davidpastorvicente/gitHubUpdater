package com.davidpv.updatermanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidpv.updatermanager.ui.MainScreen
import com.davidpv.updatermanager.ui.MainViewModel
import com.davidpv.updatermanager.ui.theme.UpdaterManagerTheme

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

            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            UpdaterManagerTheme {
                MainScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onPrimaryAction = viewModel::onPrimaryAction,
                    onOpenAppDetails = viewModel::openAppDetails,
                    onCloseAppDetails = viewModel::closeAppDetails,
                )
            }
        }
    }
}
