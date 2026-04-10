package com.davidpv.githubupdater.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidpv.githubupdater.data.AppRepository
import com.davidpv.githubupdater.data.local.AppCatalogRepository
import com.davidpv.githubupdater.data.local.AppSettingsRepository
import com.davidpv.githubupdater.data.model.AppAction
import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.AppSettings
import com.davidpv.githubupdater.data.model.AvailabilityState
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import com.davidpv.githubupdater.data.model.InstallProgress
import com.davidpv.githubupdater.data.model.InstallStage
import com.davidpv.githubupdater.data.model.ManagedApp
import com.davidpv.githubupdater.data.model.ThemeMode
import com.davidpv.githubupdater.data.model.VersionCompareDepth
import com.davidpv.githubupdater.data.remote.GitHubReleasesService
import com.davidpv.githubupdater.install.DownloadService
import com.davidpv.githubupdater.install.InstallResultEvents
import com.davidpv.githubupdater.install.InstallResultStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
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
    val pendingActionsByPackageName: Map<String, AppAction> = emptyMap(),
    val historyLoadingPackageName: String? = null,
)

class MainViewModel(
    private val appContext: Context,
    private val repository: AppRepository,
    private val catalogRepository: AppCatalogRepository,
    private val settingsRepository: AppSettingsRepository,
    private val releasesService: GitHubReleasesService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
                when (event.status) {
                    InstallResultStatus.Success -> refreshLocalStatus()
                    InstallResultStatus.Failed -> {
                        _uiState.update {
                            it.copy(errorMessage = event.failureMessage ?: "Install failed.")
                        }
                    }

                    InstallResultStatus.Cancelled -> Unit
                }
            }
        }
        viewModelScope.launch {
            DownloadService.progressEvents.collect { event ->
                when {
                    event.cancelled -> clearInstallProgress(event.packageName)
                    event.errorMessage != null -> {
                        clearInstallProgress(event.packageName)
                        _uiState.update { it.copy(errorMessage = event.errorMessage) }
                    }
                    event.progress != null -> updateInstallProgress(event.packageName, event.progress)
                }
            }
        }
        if (settingsRepository.currentSettings.refreshOnStart) {
            refresh()
        } else {
            refreshLocalStatus()
        }
    }

    fun refresh(forceRemoteRefresh: Boolean = false) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                repository.loadManagedApps(
                    forceRemoteRefresh = forceRemoteRefresh,
                    useCachedRemoteDataOnly = false,
                )
            }
                .onSuccess { apps ->
                    _uiState.update { state ->
                        applyAppsUpdate(state, apps)
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        if (error is AppRepository.PartialLoadException) {
                            applyAppsUpdate(state, error.apps).copy(
                                errorMessage = error.message,
                            )
                        } else {
                            state.copy(
                                isRefreshing = false,
                                errorMessage = error.message ?: "Failed to refresh apps.",
                            )
                        }
                    }
                }
        }
    }

    fun refreshNow() {
        refresh(forceRemoteRefresh = true)
    }

    fun refreshLocalStatus() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                repository.loadManagedApps(
                    forceRemoteRefresh = false,
                    useCachedRemoteDataOnly = true,
                )
            }.onSuccess { apps ->
                _uiState.update { state ->
                    applyAppsUpdate(state, apps)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = error.message ?: "Failed to refresh apps.",
                    )
                }
            }
        }
    }

    private fun applyAppsUpdate(state: MainUiState, apps: List<ManagedApp>): MainUiState {
        val appsByPackageName = apps.associateBy(ManagedApp::packageName)
        val retainedProgress = state.installProgressByPackageName.filter { (packageName, progress) ->
            val app = appsByPackageName[packageName]
            when {
                app == null -> false
                progress.stage != InstallStage.AwaitingConfirmation -> true
                progress.action == AppAction.Install && app.installedVersionName == null -> true
                progress.action == AppAction.Update &&
                    (app.installedVersionName == null || app.availabilityState is AvailabilityState.UpdateAvailable) -> true
                else -> false
            }
        }
        return state.copy(
            apps = apps,
            selectedPackageName = state.selectedPackageName?.takeIf { selectedPackageName ->
                apps.any { it.packageName == selectedPackageName }
            },
            isRefreshing = false,
            installProgressByPackageName = retainedProgress,
        )
    }

    fun onPrimaryAction(app: ManagedApp) {
        val asset = app.latestAsset ?: return
        if (_uiState.value.installProgressByPackageName.containsKey(app.packageName)) return

        val action = if (app.installedVersionName == null) AppAction.Install else AppAction.Update
        rememberPendingAction(app.packageName, action)
        updateInstallProgress(app.packageName, InstallProgress(stage = InstallStage.CheckingCache, action = action))
        DownloadService.startDownload(appContext, app.packageName, app.displayName, asset, action)
    }

    fun cancelInstall(packageName: String) {
        DownloadService.cancelDownload(appContext, packageName)
        clearInstallProgress(packageName)
        forgetPendingAction(packageName)
    }

    fun startUninstall(packageName: String) {
        rememberPendingAction(packageName, AppAction.Uninstall)
    }

    fun clearUninstall(packageName: String) {
        if (_uiState.value.installProgressByPackageName.containsKey(packageName)) return
        forgetPendingAction(packageName)
    }

    fun openAppDetails(packageName: String) {
        _uiState.update {
            it.copy(
                selectedPackageName = packageName,
                isSettingsOpen = false,
            )
        }
        loadHistory(packageName)
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

    fun setRefreshOnStart(enabled: Boolean) {
        settingsRepository.setRefreshOnStart(enabled)
    }

    fun setVersionCompareDepth(depth: VersionCompareDepth) {
        settingsRepository.setVersionCompareDepth(depth)
        refresh()
    }

    fun setGitHubToken(token: String) {
        settingsRepository.setGitHubToken(token)
    }

    fun useDefaultDownloadLocation() {
        settingsRepository.setCustomDownloadTreeUri(null)
    }

    fun setCustomDownloadLocation(uri: Uri) {
        settingsRepository.setCustomDownloadTreeUri(uri)
    }

    fun addApp(entry: AppCatalogEntry) {
        catalogRepository.addApp(entry)
        refresh()
    }

    fun updateApp(catalogId: Int, entry: AppCatalogEntry) {
        val oldEntry = catalogRepository.getEntry(catalogId)
        catalogRepository.updateApp(catalogId, entry)
        val repoChanged = oldEntry == null ||
            oldEntry.releaseOwner != entry.releaseOwner ||
            oldEntry.releaseRepo != entry.releaseRepo
        if (repoChanged) refresh() else refreshLocalStatus()
    }

    fun deleteApp(catalogId: Int) {
        val app = _uiState.value.apps.firstOrNull { it.catalogId == catalogId }
        catalogRepository.deleteApp(catalogId)
        _uiState.update { state ->
            state.copy(
                apps = state.apps.filter { it.catalogId != catalogId },
                selectedPackageName = state.selectedPackageName?.takeIf { it != app?.packageName },
            )
        }
    }

    fun importApps(json: String) {
        runCatching {
            val entries = importJson
                .decodeFromString<List<AppCatalogEntry>>(json)
            catalogRepository.importApps(entries)
            refresh()
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "Import failed.") }
        }
    }

    fun clearApps() {
        catalogRepository.clearApps()
        _uiState.update { state ->
            state.copy(apps = emptyList(), selectedPackageName = null)
        }
    }

    fun bulkEditApps(json: String): String? {
        return runCatching {
            val entries = importJson.decodeFromString<List<AppCatalogEntry>>(json)
            catalogRepository.importApps(entries)
            refresh()
            null as String?
        }.getOrElse { it.message ?: "Invalid JSON." }
    }

    fun exportAppsJson(): String = catalogRepository.exportAppsJson()

    fun catalogEntry(catalogId: Int): AppCatalogEntry? =
        catalogRepository.getEntry(catalogId)

    suspend fun testFetchReleases(owner: String, repo: String): List<GitHubReleaseResponse> =
        releasesService.fetchReleases(
            owner = owner,
            repo = repo,
            perPage = 10,
            forceRefresh = true,
            gitHubToken = settingsRepository.currentSettings.gitHubToken,
        )

    fun loadHistory(packageName: String, forceRemoteRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(historyLoadingPackageName = packageName) }
            runCatching {
                repository.loadHistory(packageName = packageName, forceRemoteRefresh = forceRemoteRefresh)
            }.onSuccess { history ->
                _uiState.update { state ->
                    state.copy(
                        apps = state.apps.map { app ->
                            if (app.packageName == packageName) app.copy(history = history) else app
                        },
                        historyLoadingPackageName = state.historyLoadingPackageName.takeIf { it != packageName },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        historyLoadingPackageName = it.historyLoadingPackageName.takeIf { current -> current != packageName },
                        errorMessage = error.message ?: "Failed to load version history.",
                    )
                }
            }
        }
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

    private fun rememberPendingAction(packageName: String, action: AppAction) {
        _uiState.update { state ->
            state.copy(pendingActionsByPackageName = state.pendingActionsByPackageName + (packageName to action))
        }
    }

    private fun forgetPendingAction(packageName: String) {
        _uiState.update { state ->
            state.copy(pendingActionsByPackageName = state.pendingActionsByPackageName - packageName)
        }
    }

    companion object {
        private val importJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun factory(
            appContext: Context,
            repository: AppRepository,
            catalogRepository: AppCatalogRepository,
            settingsRepository: AppSettingsRepository,
            releasesService: GitHubReleasesService,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(appContext, repository, catalogRepository, settingsRepository, releasesService) as T
            }
        }
    }
}
