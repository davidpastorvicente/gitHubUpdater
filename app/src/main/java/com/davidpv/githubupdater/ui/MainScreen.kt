package com.davidpv.githubupdater.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidpv.githubupdater.data.model.AppAction
import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.AvailabilityState
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import com.davidpv.githubupdater.data.model.InstallProgress
import com.davidpv.githubupdater.data.model.InstallStage
import com.davidpv.githubupdater.data.model.ManagedApp
import com.davidpv.githubupdater.data.model.ReleaseAsset
import com.davidpv.githubupdater.data.model.ReleaseItem
import com.davidpv.githubupdater.data.model.ThemeMode
import com.davidpv.githubupdater.ui.theme.GitHubUpdaterTheme
import java.time.Instant

private const val LIST_ROUTE = "list"
private const val DETAIL_ROUTE = "detail/{packageName}"
private const val DETAIL_ROUTE_PREFIX = "detail"
private const val SETTINGS_ROUTE = "settings"
private const val ADD_APP_ROUTE = "add_app"
private const val EDIT_APP_ROUTE = "edit_app/{catalogId}"
private const val EDIT_APP_ROUTE_PREFIX = "edit_app"
private const val EXPANDED_LAYOUT_MIN_WIDTH_DP = 840

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onCancelInstall: (String) -> Unit,
    onRequestUninstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onSetRefreshOnStart: (Boolean) -> Unit,
    onSetGitHubToken: (String) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onCloseAppDetails: () -> Unit,
    onAddApp: (AppCatalogEntry) -> Unit,
    onUpdateApp: (Int, AppCatalogEntry) -> Unit,
    onDeleteApp: (Int) -> Unit,
    onTestFetchReleases: suspend (String, String) -> List<GitHubReleaseResponse>,
    onGetCatalogEntry: (Int) -> AppCatalogEntry?,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    onClearApps: () -> Unit,
    currentAppsJson: String,
    onBulkEditApps: (String) -> String?,
) {
    val density = LocalDensity.current
    val containerWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isExpandedLayout = containerWidthDp >= EXPANDED_LAYOUT_MIN_WIDTH_DP.dp

    if (isExpandedLayout) {
        ExpandedMainScreen(
            state = state,
            onRefresh = onRefresh,
            onPrimaryAction = onPrimaryAction,
            onCancelInstall = onCancelInstall,
            onRequestUninstall = onRequestUninstall,
            onOpenSettings = onOpenSettings,
            onSetThemeMode = onSetThemeMode,
            onSetDynamicColor = onSetDynamicColor,
            onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
            onSetRefreshOnStart = onSetRefreshOnStart,
            onSetGitHubToken = onSetGitHubToken,
            onPickDownloadFolder = onPickDownloadFolder,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            onOpenAppDetails = onOpenAppDetails,
            onLoadHistory = onLoadHistory,
            onAddApp = onAddApp,
            onUpdateApp = onUpdateApp,
            onDeleteApp = onDeleteApp,
            onTestFetchReleases = onTestFetchReleases,
            onGetCatalogEntry = onGetCatalogEntry,
            onImportConfig = onImportConfig,
            onExportConfig = onExportConfig,
            onClearApps = onClearApps,
            currentAppsJson = currentAppsJson,
            onBulkEditApps = onBulkEditApps,
        )
    } else {
        CompactMainScreen(
            state = state,
            onRefresh = onRefresh,
            onPrimaryAction = onPrimaryAction,
            onCancelInstall = onCancelInstall,
            onRequestUninstall = onRequestUninstall,
            onOpenSettings = onOpenSettings,
            onCloseSettings = onCloseSettings,
            onSetThemeMode = onSetThemeMode,
            onSetDynamicColor = onSetDynamicColor,
            onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
            onSetRefreshOnStart = onSetRefreshOnStart,
            onSetGitHubToken = onSetGitHubToken,
            onPickDownloadFolder = onPickDownloadFolder,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            onOpenAppDetails = onOpenAppDetails,
            onLoadHistory = onLoadHistory,
            onCloseAppDetails = onCloseAppDetails,
            onAddApp = onAddApp,
            onUpdateApp = onUpdateApp,
            onDeleteApp = onDeleteApp,
            onTestFetchReleases = onTestFetchReleases,
            onGetCatalogEntry = onGetCatalogEntry,
            onImportConfig = onImportConfig,
            onExportConfig = onExportConfig,
            onClearApps = onClearApps,
            currentAppsJson = currentAppsJson,
            onBulkEditApps = onBulkEditApps,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactMainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onCancelInstall: (String) -> Unit,
    onRequestUninstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onSetRefreshOnStart: (Boolean) -> Unit,
    onSetGitHubToken: (String) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onCloseAppDetails: () -> Unit,
    onAddApp: (AppCatalogEntry) -> Unit,
    onUpdateApp: (Int, AppCatalogEntry) -> Unit,
    onDeleteApp: (Int) -> Unit,
    onTestFetchReleases: suspend (String, String) -> List<GitHubReleaseResponse>,
    onGetCatalogEntry: (Int) -> AppCatalogEntry?,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    onClearApps: () -> Unit,
    currentAppsJson: String,
    onBulkEditApps: (String) -> String?,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LIST_ROUTE,
    ) {
        composable(LIST_ROUTE) {
            AppListScreen(
                state = state,
                onRefresh = onRefresh,
                onPrimaryAction = onPrimaryAction,
                onCancelInstall = onCancelInstall,
                onRequestUninstall = onRequestUninstall,
                onOpenSettings = {
                    onOpenSettings()
                    navController.navigate(SETTINGS_ROUTE)
                },
                onOpenAppDetails = { packageName ->
                    onOpenAppDetails(packageName)
                    onLoadHistory(packageName)
                    navController.navigate("$DETAIL_ROUTE_PREFIX/$packageName")
                },
                onEditApp = { catalogId ->
                    navController.navigate("$EDIT_APP_ROUTE_PREFIX/$catalogId")
                },
                onAddApp = { navController.navigate(ADD_APP_ROUTE) },
            )
        }
        composable(
            route = DETAIL_ROUTE,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
            val app = state.apps.firstOrNull { it.packageName == packageName }
            if (app != null) {
                AppDetailScreen(
                    app = app,
                    isLoadingHistory = state.historyLoadingPackageName == app.packageName,
                    onBack = {
                        onCloseAppDetails()
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(SETTINGS_ROUTE) {
            SettingsRouteScreen(
                state = state,
                onBack = {
                    onCloseSettings()
                    navController.popBackStack()
                },
                onSetThemeMode = onSetThemeMode,
                onSetDynamicColor = onSetDynamicColor,
                onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
                onSetRefreshOnStart = onSetRefreshOnStart,
                onSetGitHubToken = onSetGitHubToken,
                onPickDownloadFolder = onPickDownloadFolder,
                onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
                onImportConfig = onImportConfig,
                onExportConfig = onExportConfig,
                onClearApps = onClearApps,
                currentAppsJson = currentAppsJson,
                onBulkEditApps = onBulkEditApps,
            )
        }
        composable(ADD_APP_ROUTE) {
            AddEditAppScreen(
                existingEntry = null,
                onSave = { entry ->
                    onAddApp(entry)
                    navController.popBackStack()
                },
                onDelete = null,
                onTest = onTestFetchReleases,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = EDIT_APP_ROUTE,
            arguments = listOf(navArgument("catalogId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val catalogId = backStackEntry.arguments?.getInt("catalogId") ?: return@composable
            val existingEntry = onGetCatalogEntry(catalogId) ?: return@composable
            AddEditAppScreen(
                existingEntry = existingEntry,
                onSave = { entry ->
                    onUpdateApp(catalogId, entry)
                    navController.popBackStack()
                },
                onDelete = {
                    onDeleteApp(catalogId)
                    navController.popBackStack(LIST_ROUTE, inclusive = false)
                },
                onTest = onTestFetchReleases,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedMainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onCancelInstall: (String) -> Unit,
    onRequestUninstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onSetRefreshOnStart: (Boolean) -> Unit,
    onSetGitHubToken: (String) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onAddApp: (AppCatalogEntry) -> Unit,
    onUpdateApp: (Int, AppCatalogEntry) -> Unit,
    onDeleteApp: (Int) -> Unit,
    onTestFetchReleases: suspend (String, String) -> List<GitHubReleaseResponse>,
    onGetCatalogEntry: (Int) -> AppCatalogEntry?,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    onClearApps: () -> Unit,
    currentAppsJson: String,
    onBulkEditApps: (String) -> String?,
) {
    val selectedApp = state.apps.firstOrNull { it.packageName == state.selectedPackageName }
    var editingCatalogId by remember { mutableStateOf<Int?>(null) }
    var isAddingApp by remember { mutableStateOf(false) }

    LaunchedEffect(state.apps, state.selectedPackageName) {
        if (state.selectedPackageName == null && !isAddingApp && editingCatalogId == null) {
            state.apps.firstOrNull()?.let { onOpenAppDetails(it.packageName) }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Updater") },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Settings") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(modifier = Modifier.padding(12.dp)) { Text(data.visuals.message) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                isAddingApp = true
                editingCatalogId = null
            }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add app")
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(0.42f)) {
                AppListContent(
                    state = state,
                    onRefresh = onRefresh,
                    onPrimaryAction = onPrimaryAction,
                    onCancelInstall = onCancelInstall,
                    onRequestUninstall = onRequestUninstall,
                     onOpenAppDetails = { packageName ->
                         isAddingApp = false
                         editingCatalogId = null
                         onOpenAppDetails(packageName)
                         onLoadHistory(packageName)
                     },
                    onEditApp = { catalogId ->
                        isAddingApp = false
                        editingCatalogId = catalogId
                    },
                )
            }
            Box(modifier = Modifier.weight(0.58f)) {
                when {
                    state.isSettingsOpen -> {
                        SettingsContent(
                            settings = state.settings,
                            downloadLocationSummary = state.downloadLocationSummary,
                            onSetThemeMode = onSetThemeMode,
                            onSetDynamicColor = onSetDynamicColor,
                            onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
                            onSetRefreshOnStart = onSetRefreshOnStart,
                            onSetGitHubToken = onSetGitHubToken,
                            onPickDownloadFolder = onPickDownloadFolder,
                            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
                            onImportConfig = onImportConfig,
                            onExportConfig = onExportConfig,
                            onClearApps = onClearApps,
                            currentAppsJson = currentAppsJson,
                            onBulkEditApps = onBulkEditApps,
                        )
                    }
                    isAddingApp -> {
                        AddEditAppScreen(
                            existingEntry = null,
                            onSave = { entry ->
                                onAddApp(entry)
                                isAddingApp = false
                            },
                            onDelete = null,
                            onTest = onTestFetchReleases,
                            onBack = { isAddingApp = false },
                        )
                    }
                    editingCatalogId != null -> {
                        val existingEntry = onGetCatalogEntry(editingCatalogId!!)
                        if (existingEntry != null) {
                            AddEditAppScreen(
                                existingEntry = existingEntry,
                                onSave = { entry ->
                                    onUpdateApp(editingCatalogId!!, entry)
                                    editingCatalogId = null
                                },
                                onDelete = {
                                    onDeleteApp(editingCatalogId!!)
                                    editingCatalogId = null
                                },
                                onTest = onTestFetchReleases,
                                onBack = { editingCatalogId = null },
                            )
                        }
                    }
                    selectedApp != null -> {
                        ExpandedAppDetailContent(
                            app = selectedApp,
                            isLoadingHistory = state.historyLoadingPackageName == selectedApp.packageName,
                        )
                    }
                    else -> {
                        EmptyDetailPane()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onCancelInstall: (String) -> Unit,
    onRequestUninstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onEditApp: (Int) -> Unit,
    onAddApp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Updater") },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Settings") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(modifier = Modifier.padding(12.dp)) { Text(data.visuals.message) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddApp) {
                Icon(Icons.Rounded.Add, contentDescription = "Add app")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AppListContent(
                state = state,
                onRefresh = onRefresh,
                onPrimaryAction = onPrimaryAction,
                onCancelInstall = onCancelInstall,
                onRequestUninstall = onRequestUninstall,
                onOpenAppDetails = onOpenAppDetails,
                onEditApp = onEditApp,
            )
        }
    }
}

@Composable
private fun AppListContent(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onCancelInstall: (String) -> Unit,
    onRequestUninstall: (String) -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onEditApp: (Int) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val groupedApps = state.apps.groupBy { app -> statusLabel(app.availabilityState) }
    val orderedSectionTitles = buildList {
        listOf("Update available", "Updated", "Not installed").forEach { title ->
            if (groupedApps.containsKey(title)) add(title)
        }
        groupedApps.keys
            .filterNot { it in this }
            .sorted()
            .forEach(::add)
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.apps.isEmpty() && !state.isRefreshing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No apps configured yet\nTap + to add one",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            orderedSectionTitles.forEach { sectionTitle ->
                item(key = "section-$sectionTitle") {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    items = groupedApps.getValue(sectionTitle),
                    key = { it.catalogId },
                ) { app ->
                    AppCard(
                        app = app,
                        installProgress = state.installProgressByPackageName[app.packageName],
                        pendingAction = state.pendingActionsByPackageName[app.packageName],
                        onPrimaryAction = { onPrimaryAction(app) },
                        onCancelInstall = { onCancelInstall(app.packageName) },
                        onRequestUninstall = { onRequestUninstall(app.packageName) },
                        onOpenHistory = { onOpenAppDetails(app.packageName) },
                        onEditConfig = { onEditApp(app.catalogId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRouteScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onSetRefreshOnStart: (Boolean) -> Unit,
    onSetGitHubToken: (String) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    onClearApps: () -> Unit,
    currentAppsJson: String,
    onBulkEditApps: (String) -> String?,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Back") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            settings = state.settings,
            downloadLocationSummary = state.downloadLocationSummary,
            onSetThemeMode = onSetThemeMode,
            onSetDynamicColor = onSetDynamicColor,
            onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
            onSetRefreshOnStart = onSetRefreshOnStart,
            onSetGitHubToken = onSetGitHubToken,
            onPickDownloadFolder = onPickDownloadFolder,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            modifier = Modifier.padding(innerPadding),
            onImportConfig = onImportConfig,
            onExportConfig = onExportConfig,
            onClearApps = onClearApps,
            currentAppsJson = currentAppsJson,
            onBulkEditApps = onBulkEditApps,
        )
    }
}

@Composable
private fun EmptyDetailPane() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select an app to view version history.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppCard(
    app: ManagedApp,
    installProgress: InstallProgress?,
    pendingAction: AppAction?,
    onPrimaryAction: () -> Unit,
    onCancelInstall: () -> Unit,
    onRequestUninstall: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditConfig: () -> Unit,
) {
    val uninstallInProgress = pendingAction == AppAction.Uninstall
    val isBusy = installProgress != null || uninstallInProgress
    val canCancel = installProgress?.let(::progressCanBeCancelled) == true
    val hasRemoteRelease = app.availabilityState !is AvailabilityState.NoRemoteRelease
    val isInstalled = app.installedVersionName != null
    val context = LocalContext.current
    val isActionable = app.availabilityState is AvailabilityState.UpdateAvailable ||
        app.availabilityState is AvailabilityState.NotInstalled
    val showLatestVersion = app.latestVersionName != null && isActionable
    val showInstalledVersion = app.installedVersionName != null
    val actionIcon = when (app.availabilityState) {
        is AvailabilityState.UpdateAvailable -> Icons.Rounded.SystemUpdateAlt
        else -> Icons.Rounded.Download
    }
    val actionDescription = when (app.availabilityState) {
        is AvailabilityState.UpdateAvailable -> "Update"
        else -> "Install"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                app = app,
                                modifier = Modifier.size(52.dp),
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(
                                    if (showLatestVersion && showInstalledVersion) 2.dp else 6.dp,
                                ),
                            ) {
                                Text(
                                    text = app.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                if (showLatestVersion || showInstalledVersion) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        if (showLatestVersion) {
                                            VersionLine(label = "Latest", value = app.latestVersionName)
                                        }
                                        if (showInstalledVersion) {
                                            VersionLine(label = "Installed", value = app.installedVersionName)
                                        }
                                    }
                                }
                            }
                        }

                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isUpdated = isInstalled && !isActionable && !isBusy
                        if (isUpdated) {
                            FilledIconActionButton(
                                onClick = {
                                    context.packageManager.getLaunchIntentForPackage(app.packageName)
                                        ?.let { context.startActivity(it) }
                                },
                                enabled = context.packageManager.getLaunchIntentForPackage(app.packageName) != null,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Launch,
                                    contentDescription = "Open",
                                )
                            }
                        } else {
                            FilledIconActionButton(
                                onClick = if (canCancel) onCancelInstall else onPrimaryAction,
                                enabled = if (canCancel) {
                                    true
                                } else {
                                    app.latestAsset != null && isActionable && !isBusy
                                },
                            ) {
                                if (canCancel) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cancel download",
                                    )
                                } else if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = actionIcon,
                                        contentDescription = actionDescription,
                                    )
                                }
                            }
                        }

                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                if (isInstalled && !isUpdated) {
                                    DropdownMenuItem(
                                        text = { Text("Open") },
                                        onClick = {
                                            menuExpanded = false
                                            context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                ?.let { context.startActivity(it) }
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        menuExpanded = false
                                        onEditConfig()
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                )
                                if (hasRemoteRelease) {
                                    DropdownMenuItem(
                                        text = { Text("History") },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenHistory()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                    )
                                }
                                if (isInstalled) {
                                    DropdownMenuItem(
                                        text = { Text("Uninstall") },
                                        onClick = {
                                            menuExpanded = false
                                            onRequestUninstall()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            installProgress?.let { progress ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                ) {
                    AppCardProgressBar(progress = progress)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    app: ManagedApp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installedIcon = remember(app.packageName, app.installedVersionName, context) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap()
                .asImageBitmap()
        }.getOrNull()
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        if (installedIcon != null) {
            Image(
                bitmap = installedIcon,
                contentDescription = "${app.displayName} icon",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            val monogram = remember(app.displayName) {
                app.displayName
                    .trim()
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "?"
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = monogram,
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalContentColor.current,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FilledIconActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun AppCardProgressBar(
    progress: InstallProgress,
) {
    val progressFraction = progress.progressFraction
    if (progressFraction != null) {
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (
        progress.stage == InstallStage.CheckingCache ||
        progress.stage == InstallStage.UsingCache ||
        progress.stage == InstallStage.Downloading ||
        progress.stage == InstallStage.Verifying ||
        progress.stage == InstallStage.PreparingInstall ||
        progress.stage == InstallStage.AwaitingConfirmation
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

private fun progressCanBeCancelled(progress: InstallProgress): Boolean = when (progress.stage) {
    InstallStage.CheckingCache,
    InstallStage.UsingCache,
    InstallStage.Downloading,
    InstallStage.Verifying,
    InstallStage.PreparingInstall,
    -> true
    InstallStage.AwaitingConfirmation -> false
}

@Composable
private fun VersionLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusLabel(state: AvailabilityState): String = when (state) {
    AvailabilityState.NoRemoteRelease -> "Unavailable"
    AvailabilityState.NotInstalled -> "Not installed"
    is AvailabilityState.Current -> "Updated"
    is AvailabilityState.UpdateAvailable -> "Update available"
    is AvailabilityState.InstalledVersionUnknown -> "Updated"
}


@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppCardPreview() {
    GitHubUpdaterTheme {
        AppCard(
            app = previewApps().first(),
            installProgress = null,
            pendingAction = null,
            onPrimaryAction = {},
            onCancelInstall = {},
            onRequestUninstall = {},
            onOpenHistory = {},
            onEditConfig = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1000)
@Composable
private fun ExpandedScreenPreview() {
    val apps = previewApps()
    GitHubUpdaterTheme {
        ExpandedMainScreen(
            state = MainUiState(apps = apps, selectedPackageName = apps.first().packageName),
            onRefresh = {},
            onPrimaryAction = {},
            onCancelInstall = {},
            onRequestUninstall = {},
            onOpenSettings = {},
            onSetThemeMode = {},
            onSetDynamicColor = {},
            onSetDeleteApkAfterInstall = {},
            onSetRefreshOnStart = {},
            onSetGitHubToken = {},
            onPickDownloadFolder = {},
            onUseDefaultDownloadLocation = {},
            onOpenAppDetails = {},
            onLoadHistory = {},
            onAddApp = {},
            onUpdateApp = { _, _ -> },
            onDeleteApp = {},
            onTestFetchReleases = { _, _ -> emptyList() },
            onGetCatalogEntry = { null },
            onImportConfig = {},
            onExportConfig = {},
            onClearApps = {},
            currentAppsJson = "[]",
            onBulkEditApps = { null },
        )
    }
}

private fun previewApps(): List<ManagedApp> {
    val asset = ReleaseAsset(
        id = 1,
        name = "sample-app-v1.4.0.apk",
        downloadUrl = "https://example.com/app.apk",
        sizeBytes = 54_000_000,
        sha256 = null,
        downloadCount = 120,
    )
    val history = listOf(
        ReleaseItem(
            id = 1,
            versionName = "1.4.0",
            publishedAt = Instant.parse("2026-03-28T12:00:00Z"),
            asset = asset,
            changelog = "Sample changelog entry.",
        ),
    )
    return listOf(
        ManagedApp(
            catalogId = 1,
            displayName = "Sample app",
            packageName = "com.example.sample",
            installedVersionName = "1.3.0",
            latestVersionName = "1.4.0",
            availabilityState = AvailabilityState.UpdateAvailable(
                installedVersion = "1.3.0",
                latestVersion = "1.4.0",
            ),
            latestAsset = asset,
            history = history,
        ),
    )
}
