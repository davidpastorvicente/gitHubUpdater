package com.davidpv.updatermanager.ui

import android.os.SystemClock
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidpv.updatermanager.data.AppRepository
import com.davidpv.updatermanager.data.local.AppSettingsRepository
import com.davidpv.updatermanager.data.model.AppSettings
import com.davidpv.updatermanager.data.model.InstallStage
import com.davidpv.updatermanager.data.model.InstallProgress
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.data.model.ThemeMode
import com.davidpv.updatermanager.install.InstallResultEvents
import com.davidpv.updatermanager.install.InstallResultStatus
import com.davidpv.updatermanager.install.ReleaseInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class MainUiState(
    val apps: List<ManagedApp> = emptyList(),
    val selectedPackageName: String? = null,
    val isSettingsOpen: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val downloadLocationSummary: String = "",
    val isRefreshing: Boolean = false,
    val installProgressByPackageName: Map<String, InstallProgress> = emptyMap(),
    val errorMessage: String? = null,
)

class MainViewModel(
    private val repository: AppRepository,
    private val settingsRepository: AppSettingsRepository,
    private val installer: ReleaseInstaller,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val installJobsByPackageName = mutableMapOf<String, Job>()
    private var lastRefreshElapsedRealtime = 0L

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        downloadLocationSummary = settingsRepository.downloadLocationSummary(settings),
                    )
                }
            }
        }
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

    fun refresh(forceRemoteRefresh: Boolean = false) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching { repository.loadManagedApps(forceRemoteRefresh = forceRemoteRefresh) }
                .onSuccess { apps ->
                    lastRefreshElapsedRealtime = SystemClock.elapsedRealtime()
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
                    lastRefreshElapsedRealtime = SystemClock.elapsedRealtime()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Failed to refresh apps.",
                        )
                    }
                }
        }
    }

    fun refreshNow() {
        refresh(forceRemoteRefresh = true)
    }

    fun refreshIfStale() {
        val elapsedSinceLastRefresh = SystemClock.elapsedRealtime() - lastRefreshElapsedRealtime
        if (lastRefreshElapsedRealtime == 0L || elapsedSinceLastRefresh >= AUTO_REFRESH_MIN_INTERVAL_MILLIS) {
            refresh(forceRemoteRefresh = false)
        }
    }

    fun onPrimaryAction(app: ManagedApp) {
        val asset = app.latestAsset ?: return
        if (_uiState.value.installProgressByPackageName.containsKey(app.packageName)) return

        updateInstallProgress(app.packageName, InstallProgress(stage = InstallStage.CheckingCache))
        val job = viewModelScope.launch {
            runCatching {
                installer.install(app.packageName, asset) { progress ->
                    updateInstallProgress(app.packageName, progress)
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    clearInstallProgress(app.packageName)
                } else {
                    clearInstallProgress(app.packageName)
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Install failed.")
                    }
                }
            }.also {
                installJobsByPackageName.remove(app.packageName)
            }
        }
        installJobsByPackageName[app.packageName] = job
    }

    fun cancelInstall(packageName: String) {
        installJobsByPackageName.remove(packageName)?.cancel()
        clearInstallProgress(packageName)
    }

    fun openAppDetails(packageName: String) {
        _uiState.update {
            it.copy(
                selectedPackageName = packageName,
                isSettingsOpen = false,
            )
        }
    }

    fun closeAppDetails() {
        _uiState.update { it.copy(selectedPackageName = null) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = !it.isSettingsOpen) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        settingsRepository.setThemeMode(themeMode)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setDeleteApkAfterInstall(enabled: Boolean) {
        settingsRepository.setDeleteApkAfterInstall(enabled)
    }

    fun useDefaultDownloadLocation() {
        settingsRepository.setCustomDownloadTreeUri(null)
    }

    fun setCustomDownloadLocation(uri: Uri) {
        settingsRepository.setCustomDownloadTreeUri(uri)
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
        private const val AUTO_REFRESH_MIN_INTERVAL_MILLIS = 60_000L

        fun factory(
            repository: AppRepository,
            settingsRepository: AppSettingsRepository,
            installer: ReleaseInstaller,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, settingsRepository, installer) as T
            }
        }
    }
}
