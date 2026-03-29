package com.davidpv.updatermanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidpv.updatermanager.data.AppRepository
import com.davidpv.updatermanager.data.model.InstallStage
import com.davidpv.updatermanager.data.model.InstallProgress
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.install.InstallResultEvents
import com.davidpv.updatermanager.install.InstallResultStatus
import com.davidpv.updatermanager.install.ReleaseInstaller
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val apps: List<ManagedApp> = emptyList(),
    val selectedPackageName: String? = null,
    val isRefreshing: Boolean = false,
    val installProgressByPackageName: Map<String, InstallProgress> = emptyMap(),
    val errorMessage: String? = null,
)

class MainViewModel(
    private val repository: AppRepository,
    private val installer: ReleaseInstaller,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(isRefreshing = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            InstallResultEvents.events.collectLatest { event ->
                clearInstallProgress(event.packageName)
                if (event.status == InstallResultStatus.Success) {
                    refresh()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching { repository.loadManagedApps() }
                .onSuccess { apps ->
                    _uiState.update { state ->
                        val appsByPackageName = apps.associateBy(ManagedApp::packageName)
                        val retainedProgress = state.installProgressByPackageName.filter { (packageName, progress) ->
                            val app = appsByPackageName[packageName]
                            when {
                                app == null -> false
                                progress.stage != InstallStage.AwaitingConfirmation -> true
                                app.installedVersionName == null -> true
                                else -> false
                            }
                        }
                        state.copy(
                            apps = apps,
                            selectedPackageName = state.selectedPackageName?.takeIf { selectedPackageName ->
                                apps.any { it.packageName == selectedPackageName }
                            },
                            isRefreshing = false,
                            installProgressByPackageName = retainedProgress,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Failed to refresh apps.",
                        )
                    }
                }
        }
    }

    fun onPrimaryAction(app: ManagedApp) {
        val asset = app.latestAsset ?: return
        if (_uiState.value.installProgressByPackageName.containsKey(app.packageName)) return

        updateInstallProgress(app.packageName, InstallProgress(stage = InstallStage.CheckingCache))
        viewModelScope.launch {
            runCatching {
                installer.install(app.packageName, asset) { progress ->
                    updateInstallProgress(app.packageName, progress)
                }
            }.onFailure { error ->
                clearInstallProgress(app.packageName)
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Install failed.")
                }
            }
        }
    }

    fun openAppDetails(packageName: String) {
        _uiState.update { it.copy(selectedPackageName = packageName) }
    }

    fun closeAppDetails() {
        _uiState.update { it.copy(selectedPackageName = null) }
    }

    private fun updateInstallProgress(packageName: String, progress: InstallProgress) {
        _uiState.update { state ->
            state.copy(
                installProgressByPackageName = state.installProgressByPackageName + (packageName to progress),
                errorMessage = null,
            )
        }
    }

    private fun clearInstallProgress(packageName: String) {
        _uiState.update { state ->
            state.copy(installProgressByPackageName = state.installProgressByPackageName - packageName)
        }
    }

    companion object {
        fun factory(
            repository: AppRepository,
            installer: ReleaseInstaller,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, installer) as T
            }
        }
    }
}
