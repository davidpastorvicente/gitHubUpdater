package com.davidpv.githubupdater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import com.davidpv.githubupdater.ui.theme.LocalStatusPalette
import kotlinx.coroutines.launch

data class AddEditAppState(
    val displayName: String = "",
    val packageName: String = "",
    val releaseOwner: String = "",
    val releaseRepo: String = "",
    val apkRegex: String = "",
    val versionRegex: String = "",
)

sealed interface TestResult {
    data class Success(val releaseName: String, val assetName: String, val versionName: String) : TestResult
    data class Error(val message: String) : TestResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAppScreen(
    existingEntry: AppCatalogEntry?,
    onSave: (AppCatalogEntry) -> Unit,
    onDelete: (() -> Unit)?,
    onTest: suspend (owner: String, repo: String) -> List<GitHubReleaseResponse>,
    onBack: () -> Unit,
) {
    val isEditing = existingEntry != null
    var state by remember {
        mutableStateOf(
            if (existingEntry != null) {
                AddEditAppState(
                    displayName = existingEntry.displayName,
                    packageName = existingEntry.packageName,
                    releaseOwner = existingEntry.releaseOwner,
                    releaseRepo = existingEntry.releaseRepo,
                    apkRegex = existingEntry.apkRegex.orEmpty(),
                    versionRegex = existingEntry.versionRegex.orEmpty(),
                )
            } else {
                AddEditAppState()
            }
        )
    }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showTestSpinner by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isTesting) {
        if (isTesting) {
            kotlinx.coroutines.delay(400L)
            showTestSpinner = true
        } else {
            showTestSpinner = false
        }
    }

    val canTest = state.releaseOwner.isNotBlank() && state.releaseRepo.isNotBlank()
    val canSave = state.displayName.isNotBlank() &&
        state.packageName.isNotBlank() &&
        state.releaseOwner.isNotBlank() &&
        state.releaseRepo.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit App" else "New App") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onDelete != null) {
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete app")
                        }
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete app") },
                                text = { Text("Are you sure you want to remove \"${state.displayName}\" from your catalog?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showDeleteDialog = false
                                        onDelete()
                                    }) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                            )
                        }
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
                    text = "GitHub Release Source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { state = state.copy(displayName = it); saveError = null },
                    label = { Text("Display name") },
                    placeholder = { Text("e.g. Twitter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.packageName,
                    onValueChange = { state = state.copy(packageName = it.trim()); saveError = null },
                    label = { Text("Package name") },
                    placeholder = { Text("e.g. com.twitter.android") },
                    singleLine = true,
                    enabled = !isEditing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.releaseOwner,
                        onValueChange = { state = state.copy(releaseOwner = it.trim()); testResult = null; saveError = null },
                        label = { Text("Owner") },
                        placeholder = { Text("e.g. crimera") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.releaseRepo,
                        onValueChange = { state = state.copy(releaseRepo = it.trim()); testResult = null; saveError = null },
                        label = { Text("Repository") },
                        placeholder = { Text("e.g. twitter-apk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Text(
                    text = "Filters (optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = state.apkRegex,
                    onValueChange = { state = state.copy(apkRegex = it); testResult = null; saveError = null },
                    label = { Text("APK regex") },
                    placeholder = { Text("e.g. ^twitter-piko-v.*") },
                    supportingText = { Text("Matches the APK filename without .apk suffix.\nLeave blank to match any APK.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.versionRegex,
                    onValueChange = { state = state.copy(versionRegex = it); testResult = null; saveError = null },
                    label = { Text("Version regex") },
                    placeholder = { Text("e.g. -V(.+)-release") },
                    supportingText = { Text("Extracts version from APK filename (capture group 1).\nLeave blank to use the release tag.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                testResult = runTestConfig(
                                    state = state,
                                    fetchReleases = onTest,
                                )
                                isTesting = false
                            }
                        },
                        enabled = canTest && !isTesting,
                    ) {
                        if (showTestSpinner) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                        Text("Test")
                    }

                    Button(
                        onClick = {
                            val entry = state.toEntry()
                            runCatching { onSave(entry) }
                                .onFailure { saveError = it.message }
                        },
                        enabled = canSave,
                    ) {
                        Text("Save")
                    }
                }
            }

            testResult?.let { result ->
                item { TestResultCard(result) }
            }

            saveError?.let { error ->
                item { ErrorResultCard(error) }
            }
        }
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    when (result) {
        is TestResult.Success -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Configuration works!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Release: ${result.releaseName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Matched APK: ${result.assetName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Resolved version: ${result.versionName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is TestResult.Error -> {
            ErrorResultCard(result.message)
        }
    }
}

@Composable
private fun ErrorResultCard(message: String) {
    val palette = LocalStatusPalette.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = palette.warningContainer,
            contentColor = palette.onWarningContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private suspend fun runTestConfig(
    state: AddEditAppState,
    fetchReleases: suspend (owner: String, repo: String) -> List<GitHubReleaseResponse>,
): TestResult {
    return runCatching {
        val releases = fetchReleases(state.releaseOwner, state.releaseRepo)
        if (releases.isEmpty()) return TestResult.Error("No releases found for ${state.releaseOwner}/${state.releaseRepo}.")

        val apkRegexPattern = state.apkRegex
            .takeIf { it.isNotBlank() }
            ?.removeSuffix("$")
            ?.plus("\\.apk$")
            ?.let(::Regex)

        for (release in releases) {
            if (release.draft || release.prerelease) continue
            for (asset in release.assets) {
                val matches = apkRegexPattern?.containsMatchIn(asset.name) ?: asset.name.endsWith(".apk")
                if (!matches) continue

                val versionName = if (state.versionRegex.isNotBlank()) {
                    val vr = state.versionRegex.removeSuffix("$") + "\\.apk$"
                    Regex(vr).find(asset.name)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                        ?: release.tagName
                } else {
                    release.tagName
                }

                return TestResult.Success(
                    releaseName = release.tagName,
                    assetName = asset.name,
                    versionName = versionName,
                )
            }
        }
        TestResult.Error("No matching APK asset found in the latest releases. Check your APK regex.")
    }.getOrElse { e ->
        TestResult.Error(e.message ?: "Test failed.")
    }
}

private fun AddEditAppState.toEntry() = AppCatalogEntry(
    displayName = displayName.trim(),
    packageName = packageName.trim(),
    releaseOwner = releaseOwner.trim(),
    releaseRepo = releaseRepo.trim(),
    apkRegex = apkRegex.trim().takeIf { it.isNotBlank() },
    versionRegex = versionRegex.trim().takeIf { it.isNotBlank() },
)
