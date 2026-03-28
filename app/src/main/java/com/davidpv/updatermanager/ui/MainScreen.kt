package com.davidpv.updatermanager.ui

import android.text.format.Formatter
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidpv.updatermanager.data.model.AvailabilityState
import com.davidpv.updatermanager.data.model.InstallProgress
import com.davidpv.updatermanager.data.model.InstallStage
import com.davidpv.updatermanager.data.model.ManagedApp
import com.davidpv.updatermanager.data.model.ReleaseItem
import com.davidpv.updatermanager.ui.theme.LocalStatusPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onCloseAppDetails: () -> Unit,
) {
    val selectedApp = state.apps.firstOrNull { it.id == state.selectedAppId }
    if (selectedApp != null) {
        AppDetailScreen(
            app = selectedApp,
            onBack = onCloseAppDetails,
        )
    } else {
        AppListScreen(
            state = state,
            onRefresh = onRefresh,
            onPrimaryAction = onPrimaryAction,
            onOpenAppDetails = onOpenAppDetails,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onPrimaryAction: (ManagedApp) -> Unit,
    onOpenAppDetails: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updater Manager") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.errorMessage?.let { message ->
                item { ErrorCard(message = message) }
            }

            items(state.apps, key = { it.id }) { app ->
                AppCard(
                    app = app,
                    installProgress = state.installProgressByAppId[app.id],
                    onPrimaryAction = { onPrimaryAction(app) },
                    onOpenDetails = { onOpenAppDetails(app.id) },
                )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    text = "Showing the standard Twitter APK release history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(app.history, key = { it.id }) { release ->
                HistoryRow(release = release, isLatest = release.id == app.history.firstOrNull()?.id)
            }
        }
    }
}

@Composable
private fun AppCard(
    app: ManagedApp,
    installProgress: InstallProgress?,
    onPrimaryAction: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val isBusy = installProgress != null
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
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            color = statusContainerColor(app.availabilityState),
                            contentColor = statusContentColor(app.availabilityState),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = statusLabel(app.availabilityState),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    VersionLine(label = "Latest", value = app.latestVersionName ?: "Unavailable")
                    VersionLine(label = "Installed", value = app.installedVersionName ?: "⏤")

                    installProgress?.let {
                        InstallProgressSection(progress = it)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilledIconActionButton(
                        onClick = onPrimaryAction,
                        enabled = app.latestAsset != null && app.availabilityState !is AvailabilityState.Current && !isBusy,
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionDescription,
                            )
                        }
                    }

                    FilledIconActionButton(
                        onClick = onOpenDetails,
                        enabled = true,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Open version history",
                        )
                    }
                }
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
    Button(
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun InstallProgressSection(progress: InstallProgress) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = progressLabel(progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val progressFraction = progress.progressFraction
        if (progressFraction != null) {
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (
            progress.stage == InstallStage.Downloading ||
            progress.stage == InstallStage.PreparingInstall ||
            progress.stage == InstallStage.Verifying
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (progress.downloadedBytes > 0L) {
            Text(
                text = progressBytesLabel(context, progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

@Composable
private fun statusContainerColor(state: AvailabilityState) = when (state) {
    AvailabilityState.NoRemoteRelease -> MaterialTheme.colorScheme.surfaceVariant
    AvailabilityState.NotInstalled -> MaterialTheme.colorScheme.surfaceVariant
    is AvailabilityState.Current -> LocalStatusPalette.current.successContainer
    is AvailabilityState.UpdateAvailable -> LocalStatusPalette.current.warningContainer
    is AvailabilityState.InstalledVersionUnknown -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun statusContentColor(state: AvailabilityState) = when (state) {
    AvailabilityState.NoRemoteRelease -> MaterialTheme.colorScheme.onSurfaceVariant
    AvailabilityState.NotInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
    is AvailabilityState.Current -> LocalStatusPalette.current.onSuccessContainer
    is AvailabilityState.UpdateAvailable -> LocalStatusPalette.current.onWarningContainer
    is AvailabilityState.InstalledVersionUnknown -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(state: AvailabilityState): String = when (state) {
    AvailabilityState.NoRemoteRelease -> "Unavailable"
    AvailabilityState.NotInstalled -> "Not installed"
    is AvailabilityState.Current -> "Updated"
    is AvailabilityState.UpdateAvailable -> "Update available"
    is AvailabilityState.InstalledVersionUnknown -> "Updated"
}

private fun progressLabel(progress: InstallProgress): String = when (progress.stage) {
    InstallStage.CheckingCache -> "Checking cached APK"
    InstallStage.UsingCache -> "Using cached APK"
    InstallStage.Downloading -> "Downloading APK"
    InstallStage.Verifying -> "Verifying APK"
    InstallStage.PreparingInstall -> "Preparing installer"
    InstallStage.AwaitingConfirmation -> "Waiting for install confirmation"
}

private fun progressBytesLabel(context: android.content.Context, progress: InstallProgress): String {
    val downloaded = Formatter.formatFileSize(context, progress.downloadedBytes)
    val total = progress.totalBytes?.let { Formatter.formatFileSize(context, it) }
    return if (total != null) "$downloaded / $total" else downloaded
}

private val DATE_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
