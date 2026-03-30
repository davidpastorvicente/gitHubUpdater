package com.davidpv.updatermanager.ui

import android.content.Intent
import android.content.res.Configuration
import android.text.format.Formatter
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.core.net.toUri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidpv.updatermanager.data.model.AppCatalogEntry
import com.davidpv.updatermanager.data.model.AvailabilityState
import com.davidpv.updatermanager.data.model.GitHubReleaseResponse
import com.davidpv.updatermanager.data.model.InstallProgress
import com.davidpv.updatermanager.data.model.InstallStage
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.data.model.ReleaseAsset
import com.davidpv.updatermanager.data.model.ReleaseItem
import com.davidpv.updatermanager.data.model.ThemeMode
import com.davidpv.updatermanager.ui.theme.LocalStatusPalette
import com.davidpv.updatermanager.ui.theme.UpdaterManagerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val LIST_ROUTE = "list"
private const val DETAIL_ROUTE = "detail/{packageName}"
private const val DETAIL_ROUTE_PREFIX = "detail"
private const val SETTINGS_ROUTE = "settings"
private const val ADD_APP_ROUTE = "add_app"
private const val EDIT_APP_ROUTE = "edit_app/{packageName}"
private const val EDIT_APP_ROUTE_PREFIX = "edit_app"
private const val EXPANDED_LAYOUT_MIN_WIDTH_DP = 840

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onCancelInstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onCloseAppDetails: () -> Unit,
    onAddApp: (AppCatalogEntry) -> Unit,
    onUpdateApp: (String, AppCatalogEntry) -> Unit,
    onDeleteApp: (String) -> Unit,
    onTestFetchReleases: suspend (String, String) -> List<GitHubReleaseResponse>,
    onGetCatalogEntry: (String) -> AppCatalogEntry?,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
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
            onOpenSettings = onOpenSettings,
            onSetThemeMode = onSetThemeMode,
            onSetDynamicColor = onSetDynamicColor,
            onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
            onPickDownloadFolder = onPickDownloadFolder,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            onOpenAppDetails = onOpenAppDetails,
            onAddApp = onAddApp,
            onUpdateApp = onUpdateApp,
            onDeleteApp = onDeleteApp,
            onTestFetchReleases = onTestFetchReleases,
            onGetCatalogEntry = onGetCatalogEntry,
            onImportConfig = onImportConfig,
            onExportConfig = onExportConfig,
        )
    } else {
        CompactMainScreen(
            state = state,
            onRefresh = onRefresh,
            onPrimaryAction = onPrimaryAction,
            onCancelInstall = onCancelInstall,
            onOpenSettings = onOpenSettings,
            onCloseSettings = onCloseSettings,
            onSetThemeMode = onSetThemeMode,
            onSetDynamicColor = onSetDynamicColor,
            onSetDeleteApkAfterInstall = onSetDeleteApkAfterInstall,
            onPickDownloadFolder = onPickDownloadFolder,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            onOpenAppDetails = onOpenAppDetails,
            onCloseAppDetails = onCloseAppDetails,
            onAddApp = onAddApp,
            onUpdateApp = onUpdateApp,
            onDeleteApp = onDeleteApp,
            onTestFetchReleases = onTestFetchReleases,
            onGetCatalogEntry = onGetCatalogEntry,
            onImportConfig = onImportConfig,
            onExportConfig = onExportConfig,
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
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onCloseAppDetails: () -> Unit,
    onAddApp: (AppCatalogEntry) -> Unit,
    onUpdateApp: (String, AppCatalogEntry) -> Unit,
    onDeleteApp: (String) -> Unit,
    onTestFetchReleases: suspend (String, String) -> List<GitHubReleaseResponse>,
    onGetCatalogEntry: (String) -> AppCatalogEntry?,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
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
                onOpenSettings = {
                    onOpenSettings()
                    navController.navigate(SETTINGS_ROUTE)
                },
                onOpenAppDetails = { packageName ->
                    onOpenAppDetails(packageName)
                    navController.navigate("$DETAIL_ROUTE_PREFIX/$packageName")
                },
                onEditApp = { packageName ->
                    navController.navigate("$EDIT_APP_ROUTE_PREFIX/$packageName")
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
                onPickDownloadFolder = onPickDownloadFolder,
                onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
                onImportConfig = onImportConfig,
                onExportConfig = onExportConfig,
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
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
            val existingEntry = onGetCatalogEntry(packageName) ?: return@composable
            AddEditAppScreen(
                existingEntry = existingEntry,
                onSave = { entry ->
                    onUpdateApp(packageName, entry)
                    navController.popBackStack()
                },
                onDelete = {
                    onDeleteApp(packageName)
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
    onOpenSettings: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onAddApp: (AppCatalogEntry) -> Unit,
    onUpdateApp: (String, AppCatalogEntry) -> Unit,
    onDeleteApp: (String) -> Unit,
    onTestFetchReleases: suspend (String, String) -> List<GitHubReleaseResponse>,
    onGetCatalogEntry: (String) -> AppCatalogEntry?,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
) {
    val selectedApp = state.apps.firstOrNull { it.packageName == state.selectedPackageName }
    var editingPackageName by remember { mutableStateOf<String?>(null) }
    var isAddingApp by remember { mutableStateOf(false) }

    LaunchedEffect(state.apps, state.selectedPackageName) {
        if (state.selectedPackageName == null && !isAddingApp && editingPackageName == null) {
            state.apps.firstOrNull()?.let { onOpenAppDetails(it.packageName) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updater Manager") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                isAddingApp = true
                editingPackageName = null
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
                    onOpenAppDetails = { packageName ->
                        isAddingApp = false
                        editingPackageName = null
                        onOpenAppDetails(packageName)
                    },
                    onEditApp = { packageName ->
                        isAddingApp = false
                        editingPackageName = packageName
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
                            onPickDownloadFolder = onPickDownloadFolder,
                            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
                            onImportConfig = onImportConfig,
                            onExportConfig = onExportConfig,
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
                    editingPackageName != null -> {
                        val existingEntry = onGetCatalogEntry(editingPackageName!!)
                        if (existingEntry != null) {
                            AddEditAppScreen(
                                existingEntry = existingEntry,
                                onSave = { entry ->
                                    onUpdateApp(editingPackageName!!, entry)
                                    editingPackageName = null
                                },
                                onDelete = {
                                    onDeleteApp(editingPackageName!!)
                                    editingPackageName = null
                                },
                                onTest = onTestFetchReleases,
                                onBack = { editingPackageName = null },
                            )
                        }
                    }
                    selectedApp != null -> {
                        ExpandedAppDetailContent(
                            app = selectedApp,
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
    onOpenSettings: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onEditApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updater Manager") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
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
    onOpenAppDetails: (String) -> Unit,
    onEditApp: (String) -> Unit,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.errorMessage?.let { message ->
                item { ErrorCard(message = message) }
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
                    key = { it.packageName },
                ) { app ->
                    AppCard(
                        app = app,
                        installProgress = state.installProgressByPackageName[app.packageName],
                        onPrimaryAction = { onPrimaryAction(app) },
                        onCancelInstall = { onCancelInstall(app.packageName) },
                        onOpenHistory = { onOpenAppDetails(app.packageName) },
                        onEditConfig = { onEditApp(app.packageName) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailScreen(
    app: ManagedApp,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AppDetailContent(app = app)
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
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
            onPickDownloadFolder = onPickDownloadFolder,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            modifier = Modifier.padding(innerPadding),
            onImportConfig = onImportConfig,
            onExportConfig = onExportConfig,
        )
    }
}

@Composable
private fun AppDetailContent(app: ManagedApp) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Version history",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Text(
                text = "Showing the available release history for this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(app.history, key = { it.id }) { release ->
            HistoryRow(release = release, isLatest = release.id == app.history.firstOrNull()?.id)
        }
    }
}

@Composable
private fun ExpandedAppDetailContent(
    app: ManagedApp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AppDetailContent(app = app)
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
    onPrimaryAction: () -> Unit,
    onCancelInstall: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditConfig: () -> Unit,
) {
    val isBusy = installProgress != null
    val canCancel = installProgress?.let(::progressCanBeCancelled) == true
    val hasRemoteRelease = app.availabilityState !is AvailabilityState.NoRemoteRelease
    val isInstalled = app.installedVersionName != null
    val context = LocalContext.current
    val showLatestVersion = app.latestVersionName != null && app.availabilityState !is AvailabilityState.Current
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
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = app.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                if (showLatestVersion) {
                                    VersionLine(label = "Latest", value = app.latestVersionName)
                                }
                                if (showInstalledVersion) {
                                    VersionLine(label = "Installed", value = app.installedVersionName)
                                }
                            }
                        }

                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconActionButton(
                            onClick = if (canCancel) onCancelInstall else onPrimaryAction,
                            enabled = if (canCancel) {
                                true
                            } else {
                                app.latestAsset != null && app.availabilityState !is AvailabilityState.Current && !isBusy
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
                                if (isInstalled) {
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
                                            val intent = Intent(Intent.ACTION_DELETE,
                                                "package:${app.packageName}".toUri())
                                            context.startActivity(intent)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun HistoryRow(
    release: ReleaseItem,
    isLatest: Boolean,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = release.versionName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = DATE_FORMATTER.format(release.publishedAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Formatter.formatFileSize(context, release.asset.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isLatest) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "Latest",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val palette = LocalStatusPalette.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = palette.warningContainer,
            contentColor = palette.onWarningContainer,
        ),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun statusLabel(state: AvailabilityState): String = when (state) {
    AvailabilityState.NoRemoteRelease -> "Unavailable"
    AvailabilityState.NotInstalled -> "Not installed"
    is AvailabilityState.Current -> "Updated"
    is AvailabilityState.UpdateAvailable -> "Update available"
    is AvailabilityState.InstalledVersionUnknown -> "Updated"
}

private val DATE_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppCardPreview() {
    UpdaterManagerTheme {
        AppCard(
            app = previewApps().first(),
            installProgress = null,
            onPrimaryAction = {},
            onCancelInstall = {},
            onOpenHistory = {},
            onEditConfig = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1000)
@Composable
private fun ExpandedScreenPreview() {
    val apps = previewApps()
    UpdaterManagerTheme {
        ExpandedMainScreen(
            state = MainUiState(apps = apps, selectedPackageName = apps.first().packageName),
            onRefresh = {},
            onPrimaryAction = {},
            onCancelInstall = {},
            onOpenSettings = {},
            onSetThemeMode = {},
            onSetDynamicColor = {},
            onSetDeleteApkAfterInstall = {},
            onPickDownloadFolder = {},
            onUseDefaultDownloadLocation = {},
            onOpenAppDetails = {},
            onAddApp = {},
            onUpdateApp = { _, _ -> },
            onDeleteApp = {},
            onTestFetchReleases = { _, _ -> emptyList() },
            onGetCatalogEntry = { null },
            onImportConfig = {},
            onExportConfig = {},
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
        ),
    )
    return listOf(
        ManagedApp(
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
