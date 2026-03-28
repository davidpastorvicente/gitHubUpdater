package com.davidpv.updatermanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidpv.updatermanager.data.AppRepository
import com.davidpv.updatermanager.data.model.InstallStage
import com.davidpv.updatermanager.data.model.InstallProgress
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.install.ReleaseInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val apps: List<ManagedApp> = emptyList(),
    val selectedAppId: String? = null,
    val isRefreshing: Boolean = false,
    val installProgressByAppId: Map<String, InstallProgress> = emptyMap(),
    val errorMessage: String? = null,
)

class MainViewModel(
    private val repository: AppRepository,
    private val installer: ReleaseInstaller,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(isRefreshing = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching { repository.loadManagedApps() }
                .onSuccess { apps ->
                    _uiState.update { state ->
                        val appsById = apps.associateBy(ManagedApp::id)
                        val retainedProgress = state.installProgressByAppId.filter { (appId, progress) ->
                            val app = appsById[appId]
                            when {
                                app == null -> false
                                progress.stage != InstallStage.AwaitingConfirmation -> true
                                app.installedVersionName == null -> true
                                else -> false
                            }
                        }
                        state.copy(
                            apps = apps,
                            selectedAppId = state.selectedAppId?.takeIf { selectedId ->
                                apps.any { it.id == selectedId }
                            },
                            isRefreshing = false,
                            installProgressByAppId = retainedProgress,
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
        if (_uiState.value.installProgressByAppId.containsKey(app.id)) return

        updateInstallProgress(app.id, InstallProgress(stage = InstallStage.CheckingCache))
        viewModelScope.launch {
            runCatching {
                installer.install(asset) { progress ->
                    updateInstallProgress(app.id, progress)
                }
            }.onFailure { error ->
                clearInstallProgress(app.id)
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Install failed.")
                }
            }
        }
    }

    fun openAppDetails(appId: String) {
        _uiState.update { it.copy(selectedAppId = appId) }
    }

    fun closeAppDetails() {
        _uiState.update { it.copy(selectedAppId = null) }
    }

    private fun updateInstallProgress(appId: String, progress: InstallProgress) {
        _uiState.update { state ->
            state.copy(
                installProgressByAppId = state.installProgressByAppId + (appId to progress),
                errorMessage = null,
            )
        }
    }

    private fun clearInstallProgress(appId: String) {
        _uiState.update { state ->
            state.copy(installProgressByAppId = state.installProgressByAppId - appId)
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
